package rh.system.shell;

import java.io.*;
import java.net.*;
import org.json.*;
import android.text.TextUtils;
import java.util.regex.*;

public class AIHelper {

    private MainActivity activity;
    private final String CONFIG_FILE;

    public AIHelper(MainActivity activity) {
        this.activity = activity;
        CONFIG_FILE = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() 
			+ "/terminal/ai/config.json";
    }

    public void setConfig(String provider, String model, String apiKey) {
        try {
            File f = new File(CONFIG_FILE);
            f.getParentFile().mkdirs();

            JSONObject json = new JSONObject();
            json.put("provider", provider.toLowerCase());
            json.put("model", model);
            json.put("apikey", apiKey);

            FileWriter writer = new FileWriter(f);
            writer.write(json.toString());
            writer.close();

            activity.formatAndAppendLog("✅ Config AI disimpan!");
            activity.formatAndAppendLog("Provider: " + provider + " | Model: " + model);
        } catch (Exception e) {
            activity.formatAndAppendLog("Gagal simpan config: " + e.getMessage());
        }
    }

    public JSONObject getConfig() {
        try {
            File f = new File(CONFIG_FILE);
            if (!f.exists()) return null;

            BufferedReader br = new BufferedReader(new FileReader(f));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            return new JSONObject(sb.toString());
        } catch (Exception e) { return null; }
    }

    public void askAI(final String explicitProvider, final String prompt, final int tabIndex) {
        new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						JSONObject config = getConfig();
						if (config == null) {
							logToUi("⚠️ Config belum ada! Ketik: ai [provider] -m [model] -k [key]", tabIndex);
							return;
						}

						String provider = (explicitProvider != null) ? explicitProvider : config.getString("provider");
						String model = config.getString("model");
						String apiKey = config.getString("apikey");

						logToUi("🤖 " + provider.toUpperCase() + " is thinking...", tabIndex);

						String responseText = "";
						if (provider.equals("gemini")) {
							responseText = requestGemini(model, apiKey, prompt);
						} else if (provider.equals("chatgpt") || provider.equals("deepseek")) {
							String url = provider.equals("deepseek") ? 
								"https://api.deepseek.com/chat/completions" : 
								"https://api.openai.com/v1/chat/completions";
							responseText = requestOpenAICompatible(url, model, apiKey, prompt);
						} else {
							logToUi("❌ Provider tidak dikenal: " + provider, tabIndex);
							return;
						}

						final String htmlResult = parseMarkdown(responseText);

						activity.runOnUiThread(new Runnable() {
								public void run() {
									activity.appendHtmlToTab(htmlResult + "<br><br>", tabIndex);
								}
							});

					} catch (Exception e) {
						logToUi("❌ Error AI: " + e.getMessage(), tabIndex);
					}
				}
			}).start();
    }

    private String requestGemini(String model, String key, String prompt) throws Exception {
        String urlStr = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + key;
        JSONObject body = new JSONObject();
        JSONObject part = new JSONObject().put("text", prompt);
        JSONArray parts = new JSONArray().put(part);
        body.put("contents", new JSONArray().put(new JSONObject().put("parts", parts)));

        return postRequest(urlStr, body.toString());
    }

    private String requestOpenAICompatible(String urlStr, String model, String key, String prompt) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "user").put("content", prompt));
        body.put("messages", messages);

        return postRequest(urlStr, body.toString(), key);
    }

    private String postRequest(String urlStr, String jsonBody, String... bearerToken) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        if (bearerToken.length > 0) {
            conn.setRequestProperty("Authorization", "Bearer " + bearerToken[0]);
        }
        conn.setDoOutput(true);

        OutputStream os = conn.getOutputStream();
        os.write(jsonBody.getBytes());
        os.flush(); os.close();

        int code = conn.getResponseCode();
        InputStream stream = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream();

        BufferedReader br = new BufferedReader(new InputStreamReader(stream));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();

        if (code >= 400) throw new Exception("API Error (" + code + "): " + sb.toString());

        JSONObject res = new JSONObject(sb.toString());

        if (jsonBody.contains("contents")) {
			return res.getJSONArray("candidates").getJSONObject(0)
				.getJSONObject("content").getJSONArray("parts")
				.getJSONObject(0).getString("text");
        } else {
			return res.getJSONArray("choices").getJSONObject(0)
				.getJSONObject("message").getString("content");
        }
    }

    private String parseMarkdown(String text) {
        text = TextUtils.htmlEncode(text);

        Pattern codeBlock = Pattern.compile("```(.*?)```", Pattern.DOTALL);
        Matcher m = codeBlock.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String codeContent = m.group(1).trim();
            String encodedCode = "";
            try { encodedCode = URLEncoder.encode(codeContent, "UTF-8"); } catch(Exception e){}

            String htmlCode = "<br><div style='background-color:#222; padding:5px; border:1px solid #444;'>" +
				"<font face='monospace' color='#A5D6A7'>" + codeContent.replace("\n", "<br>") + "</font><br>" +
				"<a href='copycode://" + encodedCode + "'><b><font color='#FFFF00'>[📋 COPY CODE]</font></b></a>" +
				"</div><br>";
            m.appendReplacement(sb, Matcher.quoteReplacement(htmlCode));
        }
        m.appendTail(sb);
        text = sb.toString();

        text = text.replaceAll("\\*\\*(.*?)\\*\\*", "<b><font color='#FFFFFF'>$1</font></b>");

        text = text.replaceAll("\\*(.*?)\\*", "<i>$1</i>");

        text = text.replaceAll("`(.*?)`", "<font face='monospace' color='#FFCC80' style='background:#333'>$1</font>");

        text = text.replace("\n", "<br>");

        return text;
    }

    private void logToUi(final String msg, final int tabIndex) {
        activity.runOnUiThread(new Runnable() {
				public void run() { activity.appendHtmlToTab("<font color='#AAAAAA'>" + msg + "</font>", tabIndex); }
			});
    }
}
