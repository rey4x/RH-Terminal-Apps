package rh.system.shell;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.text.Html;
import android.text.InputType;
import android.text.Spannable;
import android.text.method.LinkMovementMethod;
import android.text.style.URLSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.ClipboardManager;
import android.content.ClipData;
import java.io.File;
import java.net.URLDecoder;
import java.util.ArrayList;
import org.json.*;

import rh.system.shell.StorageHelper;
import rh.system.shell.AddonHelper;
import rh.system.shell.NetworkHelper;
import rh.system.shell.FileManager;
import rh.system.shell.AIHelper;

public class MainActivity extends Activity {

    public TextView tvOutput;
    public EditText etInput;
    public ScrollView scrollView;
    public LinearLayout tabContainer;

    public StorageHelper storageHelper;
    public AddonHelper addonHelper;
    public NetworkHelper networkHelper;
    public FileManager fileManager;
    public AIHelper aiHelper;
	public File currentDir;

    public static java.util.ArrayList<StringBuilder> tabData = new java.util.ArrayList<>();
    public static java.util.ArrayList<String> tabNames = new java.util.ArrayList<>();
    public static java.util.ArrayList<String> tabInputs = new java.util.ArrayList<>();
    public static int currentTabIndex = 0;
    public static MainActivity instance;
    public ArrayList<String> cmdHistory = new ArrayList<>();
    public int historyIndex = -1;
    private String BASE_DIR; 
    private String ADDON_DIR;

    private Html.ImageGetter imageGetter = new Html.ImageGetter() {
        @Override
        public Drawable getDrawable(String source) {
            try {
                Drawable d = Drawable.createFromPath(source);
                if (d != null) { d.setBounds(0, 0, 120, 120); }
                return d;
            } catch (Exception e) { return null; }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
		instance = this;
        setContentView(R.layout.main);

        BASE_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/terminal";
        ADDON_DIR = getFilesDir().getAbsolutePath() + "/addons"; 
        currentDir = Environment.getExternalStorageDirectory();

        updatePromptUI();
        new File(BASE_DIR).mkdirs();
        new File(ADDON_DIR).mkdirs();

        tvOutput = findViewById(R.id.tvOutput);
        etInput = findViewById(R.id.etInput);
        scrollView = findViewById(R.id.scroll);
        tabContainer = findViewById(R.id.tabContainer);

        tvOutput.setMovementMethod(new LinkMovementMethod() {
				@Override
				public boolean onTouchEvent(TextView widget, Spannable buffer, android.view.MotionEvent event) {
					int action = event.getAction();
					if (action == android.view.MotionEvent.ACTION_UP) {
						int x = (int) event.getX(); int y = (int) event.getY();
						x -= widget.getTotalPaddingLeft(); y -= widget.getTotalPaddingTop();
						x += widget.getScrollX(); y += widget.getScrollY();
						android.text.Layout layout = widget.getLayout();
						int line = layout.getLineForVertical(y);
						int off = layout.getOffsetForHorizontal(line, x);
						URLSpan[] link = buffer.getSpans(off, off, URLSpan.class);
						if (link.length != 0) {
							String url = link[0].getURL();
							if (url.startsWith("copycode://")) {
								try {
									String code = URLDecoder.decode(url.substring(11), "UTF-8");
									ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
									ClipData clip = ClipData.newPlainText("Copied Code", code);
									clipboard.setPrimaryClip(clip);
									Toast.makeText(MainActivity.this, "Code Copied!", Toast.LENGTH_SHORT).show();
								} catch(Exception e){}
								return true;
							}
						}
					}
					return super.onTouchEvent(widget, buffer, event);
				}
			});

        storageHelper = new StorageHelper(this);
        addonHelper = new AddonHelper(this, ADDON_DIR);
        networkHelper = new NetworkHelper(this);
        fileManager = new FileManager(this);
        aiHelper = new AIHelper(this);

        setupShortcutButtons();
		
        android.widget.ImageButton btnSettings = findViewById(R.id.btnSettings);
        btnSettings.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					startActivity(new Intent(MainActivity.this, SettingsActivity.class));
				}
			});

    
        applyPreferences();
        if (tabData.isEmpty()) {
			storageHelper.loadSessions(); 
		} else {
			renderTabs();
			switchTab(currentTabIndex);
		}

        etInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                    if (actionId == EditorInfo.IME_ACTION_DONE || 
                        (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                        String cmd = etInput.getText().toString();
                        appendHtmlToTab("<font color='#00FFFF'><b>$</b></font> <font color='#FFFFFF'>" + cmd + "</font>", currentTabIndex);
                        processCommand(cmd);
                        etInput.setText("");
                        return true;
                    }
                    return false;
                }
            });

        View.OnClickListener focusListener = new View.OnClickListener() {
            public void onClick(View v) { focusInput(); }
        };
        findViewById(R.id.rootLayout).setOnClickListener(focusListener);
        tvOutput.setOnClickListener(focusListener);
    }

    private void setupShortcutButtons() {
        Button btnUp = findViewById(R.id.btnUp);
        Button btnDown = findViewById(R.id.btnDown);
        Button btnEsc = findViewById(R.id.btnEsc);
        Button btnLs = findViewById(R.id.btnLs);

        btnUp.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if(!cmdHistory.isEmpty() && historyIndex > 0) { historyIndex--; etInput.setText(cmdHistory.get(historyIndex)); etInput.setSelection(etInput.getText().length()); } } });
        btnDown.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { if(!cmdHistory.isEmpty() && historyIndex < cmdHistory.size()-1) { historyIndex++; etInput.setText(cmdHistory.get(historyIndex)); etInput.setSelection(etInput.getText().length()); } else { historyIndex=cmdHistory.size(); etInput.setText(""); } } });
        btnEsc.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { etInput.setText(""); } });
        btnLs.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { processCommand("ls"); appendHtmlToTab("$ ls", currentTabIndex); } });
    }

    public ProcessCommand cmdProcessor;

	public void processCommand(String cmd) {
		if (cmdProcessor == null) {
			cmdProcessor = new ProcessCommand(this);
		}
		cmdProcessor.execute(cmd);
	}

    public void appendHtmlToTab(String html, int targetTabIndex) {
        if (targetTabIndex >= tabData.size()) return;

        StringBuilder sb = tabData.get(targetTabIndex);
        boolean butuhEnter = (sb.length() > 0 && !sb.toString().endsWith("<br>"));
        if (butuhEnter) html = "<br>" + html;

        sb.append(html);

        if (targetTabIndex == currentTabIndex) {
            tvOutput.append(Html.fromHtml(html, imageGetter, null));
            scrollToBottom();
        }
        storageHelper.saveSessions();
    }
	
	public void appendBackgroundSafe(String html, final int targetTabIndex) {
        if (targetTabIndex >= tabData.size()) return;

        StringBuilder sb = tabData.get(targetTabIndex);
        boolean butuhEnter = (sb.length() > 0 && !sb.toString().endsWith("<br>"));
        final String finalHtml = butuhEnter ? "<br>" + html : html;

        sb.append(finalHtml);

        try { storageHelper.saveSessions(); } catch(Exception e){}

        if (MainActivity.instance != null && !MainActivity.instance.isFinishing()) {
            MainActivity.instance.runOnUiThread(new Runnable() {
					public void run() {
						if (MainActivity.instance.currentTabIndex == targetTabIndex && MainActivity.instance.tvOutput != null) {
							MainActivity.instance.tvOutput.append(android.text.Html.fromHtml(finalHtml, MainActivity.instance.imageGetter, null));
							MainActivity.instance.scrollToBottom();
						}
					}
				});
        }
    }

    public void appendRawHtml(String html) {
        appendHtmlToTab(html, currentTabIndex);
    }

    public void formatAndAppendLog(String text) {
        text = text.replace("Error:", "<font color='#FF5555'>Error:</font>");
        text = text.replace("Success:", "<font color='#00FF00'>Success:</font>");
        appendRawHtml("<font color='#CCCCCC'>" + text + "</font><br>");
    }

    public void updateLastLine(String newHtml) {
        appendRawHtml(newHtml);
    }

    public String formatJsonToTable(String json) throws JSONException {
        try {
            String formatted = "";
            if (json.startsWith("{")) formatted = new JSONObject(json).toString(2);
            else if (json.startsWith("[")) formatted = new JSONArray(json).toString(2);
            else return json; 

            formatted = formatted.replaceAll("\"([^\"]*)\":", "<font color='#4DD0E1'>\"$1\"</font>:"); 
            formatted = formatted.replaceAll(": \"([^\"]*)\"", ": <font color='#A5D6A7'>\"$1\"</font>"); 
            formatted = formatted.replaceAll(": (true|false)", ": <font color='#EA80FC'><b>$1</b></font>"); 
            formatted = formatted.replaceAll(": (\\d+)", ": <font color='#FFB74D'>$1</font>"); 
            formatted = formatted.replaceAll(": null", ": <font color='#EF5350'>null</font>"); 
            return formatted.replace(" ", "&nbsp;").replace("\n", "<br>");
        } catch (Exception e) { return json; }
    }

    public boolean safRename(String path, String newName) { return storageHelper.safRename(path, newName); }
    public boolean safMove(String src, String dest) { return storageHelper.safMove(src, dest); }

    public void renderTabs() {
        tabContainer.removeAllViews();
        for (int i = 0; i < tabData.size(); i++) {
            final int index = i;
            Button btn = new Button(this);
            btn.setText(tabNames.get(i));
            btn.setTextSize(12);
            if (i == currentTabIndex) {
                btn.setTextColor(Color.GREEN);
                btn.setBackgroundColor(Color.parseColor("#444444"));
            } else {
                btn.setTextColor(Color.GRAY);
                btn.setBackgroundColor(Color.TRANSPARENT);
            }
            btn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { switchTab(index); } });
            btn.setOnLongClickListener(new View.OnLongClickListener() { public boolean onLongClick(View v) { showRenameDialog(index); return true; } });
            tabContainer.addView(btn);
        }
        Button btnAdd = new Button(this);
        btnAdd.setText("+");
        btnAdd.setTextColor(Color.CYAN);
        btnAdd.setBackgroundColor(Color.TRANSPARENT);
        btnAdd.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { addNewTab(); }});
        tabContainer.addView(btnAdd);
    }

    private void showRenameDialog(final int index) {
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("atur sesi");

        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(40, 20, 40, 20);

        final android.widget.EditText input = new android.widget.EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        input.setText(tabNames.get(index));
        layout.addView(input);

        final android.widget.CheckBox chkBg = new android.widget.CheckBox(this);
        chkBg.setText("biarkan jalan di background");
        layout.addView(chkBg);

        builder.setView(layout);

        builder.setPositiveButton("simpan", new android.content.DialogInterface.OnClickListener() { 
				public void onClick(android.content.DialogInterface dialog, int which) {
					String newName = input.getText().toString();
					if(!newName.isEmpty()) { 
						tabNames.set(index, newName); 
						renderTabs(); 
						storageHelper.saveSessions(); 
					}

					Intent svc = new Intent(MainActivity.this, TerminalService.class);
					if (chkBg.isChecked()) {
						startService(svc);
						android.widget.Toast.makeText(MainActivity.this, "background aktif", android.widget.Toast.LENGTH_SHORT).show();
					} else {
						stopService(svc);
						android.widget.Toast.makeText(MainActivity.this, "background mati", android.widget.Toast.LENGTH_SHORT).show();
					}
				}
			});
        builder.setNeutralButton("hapus", new android.content.DialogInterface.OnClickListener() { 
				public void onClick(android.content.DialogInterface dialog, int which) { 
					closeTab(index); 
				}
			});
        builder.setNegativeButton("batal", new android.content.DialogInterface.OnClickListener() { 
				public void onClick(android.content.DialogInterface dialog, int which) { 
					dialog.cancel(); 
				}
			});
        builder.create().show();
    }

    private void closeTab(int index) {
		if (tabData.size() <= 1) {
			tabData.set(0, new StringBuilder()); 
			tabNames.set(0, "SESI 1"); 
			if (tabInputs.size() > 0) tabInputs.set(0, "");
			tvOutput.setText(""); 
			switchTab(0);
		} else {
			tabData.remove(index); 
			tabNames.remove(index);
			if (index < tabInputs.size()) tabInputs.remove(index);

			if (currentTabIndex >= tabData.size()) currentTabIndex = tabData.size() - 1;
			else if (index < currentTabIndex) currentTabIndex--;
			switchTab(currentTabIndex);
		}
		storageHelper.saveSessions();
	}

    public void switchTab(int index) {
		if (index < 0 || index >= tabData.size()) return;

		while (tabInputs.size() < tabData.size()) tabInputs.add("");
		if (currentTabIndex >= 0 && currentTabIndex < tabInputs.size()) {
			tabInputs.set(currentTabIndex, etInput.getText().toString());
		}

		currentTabIndex = index;
		tvOutput.setText(Html.fromHtml(tabData.get(index).toString(), imageGetter, null));

		if (index < tabInputs.size()) {
			etInput.setText(tabInputs.get(index));
			etInput.setSelection(etInput.getText().length());
		}

		renderTabs();
		scrollToBottom();
	}

    private void addNewTab() {
		tabData.add(new StringBuilder("New Session...<br>"));
		tabNames.add("SESI " + (tabData.size()));
		tabInputs.add(""); 
		switchTab(tabData.size() - 1);
		storageHelper.saveSessions();
	}

    private void scrollToBottom() {
        scrollView.post(new Runnable() { 
				public void run() { 
					scrollView.fullScroll(View.FOCUS_DOWN); 
				}
			});
    }

    private void focusInput() {
        etInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(etInput, InputMethodManager.SHOW_IMPLICIT);
    }

    public void runLocalCommand(final String cmd, final int targetTab) { 
        new Thread(new Runnable() { 
				public void run() { 
					try { 
						Process p = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd}); 

						java.io.BufferedReader r = new java.io.BufferedReader(new java.io.InputStreamReader(p.getInputStream())); 
						String l; 
						while((l=r.readLine())!=null) { 
							final String f=l; 
							runOnUiThread(new Runnable() { 
									public void run() { 
										appendHtmlToTab("<font color='#CCCCCC'>" + f + "</font>", targetTab); 
									}
								}); 
						} 

						java.io.BufferedReader rErr = new java.io.BufferedReader(new java.io.InputStreamReader(p.getErrorStream())); 
						String lErr; 
						while((lErr=rErr.readLine())!=null) { 
							final String fErr=lErr; 
							runOnUiThread(new Runnable() { 
									public void run() { 
										appendHtmlToTab("<font color='#FF5555'>" + fErr + "</font>", targetTab); 
									}
								}); 
						}

					} catch(final Exception e) { 
						runOnUiThread(new Runnable() { 
								public void run() { 
									appendHtmlToTab("<font color='#FF5555'>System Error: " + e.getMessage() + "</font>", targetTab); 
								}
							});
					} 
				} 
			}).start(); 
    }
	@Override
    protected void onResume() {
        super.onResume();
        applyPreferences();
    }

    private void applyPreferences() {
        android.content.SharedPreferences prefs = getSharedPreferences("TerminalSettings", MODE_PRIVATE);

        int size = prefs.getInt("textSize", 14);
        tvOutput.setTextSize(size);
        etInput.setTextSize(size);

        TextView tvPrompt = (TextView) findViewById(R.id.tvPrompt);
        if (tvPrompt != null) {
            tvPrompt.setTextSize(size);
            String promptStr = prefs.getString("prompt", "$ ");
            tvPrompt.setText(promptStr);
        }
    }
	public void updatePromptUI() {
        // ambil settingan prompt (misal: root@user)
        android.content.SharedPreferences prefs = getSharedPreferences("TerminalSettings", MODE_PRIVATE);
        String basePrompt = prefs.getString("prompt", "$");

        basePrompt = basePrompt.replace("$", "").trim();

        String shortPath = currentDir.getName();
        if (currentDir.equals(Environment.getExternalStorageDirectory())) {
            shortPath = "~";
        }

        TextView tvPrompt = (TextView) findViewById(R.id.tvPrompt);
        if (tvPrompt != null) {
            tvPrompt.setText(basePrompt + ":" + shortPath + " $ ");
        }
    }
}
