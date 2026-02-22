package rh.system.shell;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {

    private SeekBar seekTextSize;
    private TextView tvSizePreview, tvPreviewSample;
    private EditText etCustomPrompt;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings);

        seekTextSize = (SeekBar) findViewById(R.id.seekTextSize);
        tvSizePreview = (TextView) findViewById(R.id.tvSizePreview);
        tvPreviewSample = (TextView) findViewById(R.id.tvPreviewSample);
        etCustomPrompt = (EditText) findViewById(R.id.etCustomPrompt);
        btnSave = (Button) findViewById(R.id.btnSaveConfig);

        final SharedPreferences prefs = getSharedPreferences("TerminalSettings", MODE_PRIVATE);
        int savedSize = prefs.getInt("textSize", 14);
        String savedPrompt = prefs.getString("prompt", "$ ");

        seekTextSize.setProgress(savedSize);
        tvSizePreview.setText(savedSize + "sp");
        tvPreviewSample.setTextSize(savedSize);
        etCustomPrompt.setText(savedPrompt);

        seekTextSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
					if (progress < 6) progress = 6;
					tvSizePreview.setText(progress + "sp");
					tvPreviewSample.setTextSize(progress);
				}
				@Override public void onStartTrackingTouch(SeekBar seekBar) {}
				@Override public void onStopTrackingTouch(SeekBar seekBar) {}
			});

        btnSave.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					int finalSize = seekTextSize.getProgress();
					if (finalSize < 6) finalSize = 6;
					String finalPrompt = etCustomPrompt.getText().toString();

					SharedPreferences.Editor editor = prefs.edit();
					editor.putInt("textSize", finalSize);
					editor.putString("prompt", finalPrompt);
					editor.commit();

					Toast.makeText(SettingsActivity.this, "Disimpan! Kembali ke terminal...", Toast.LENGTH_SHORT).show();
					finish();
				}
			});
    }
}
