package rh.system.shell;

import java.io.*;
import java.net.*;
import com.jcraft.jsch.*;
import org.json.*;
import java.util.regex.*;
import java.util.ArrayList;

public class NetworkHelper {

    private MainActivity activity;
    private ServerSocket serverSocket;
    private boolean isServerRunning = false;
    private SocksHandler socksHandler; 

    public NetworkHelper(MainActivity activity) {
        this.activity = activity;
    }

    public void runCurl(final String cmdLine) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    ArrayList<String> args = new ArrayList<>();
                    Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(cmdLine);
                    while (m.find()) {
                        args.add(m.group(1).replaceAll("^\"|\"$", "")); 
                    }

                    String urlString = "";
                    String method = "GET";
                    String data = null;
                    String outFile = null;
                    ArrayList<String[]> headers = new ArrayList<>();

                    for (int i = 1; i < args.size(); i++) {
                        String arg = args.get(i);
                        if (arg.equals("-X") && i + 1 < args.size()) {
                            method = args.get(++i).toUpperCase();
                        } else if (arg.equals("-d") && i + 1 < args.size()) {
                            data = args.get(++i);
                            if (method.equals("GET")) method = "POST"; 
                        } else if (arg.equals("-H") && i + 1 < args.size()) {
                            String[] h = args.get(++i).split(":", 2);
                            if (h.length == 2) headers.add(new String[]{h[0].trim(), h[1].trim()});
                        } else if (arg.equals("-o") && i + 1 < args.size()) {
                            outFile = args.get(++i);
                        } else if (arg.startsWith("http")) {
                            urlString = arg;
                        }
                    }

                    if (urlString.isEmpty()) {
                        logOnUi("Error: link url nggak ketemu.");
                        return;
                    }

                    URL url = new URL(urlString);
                    HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod(method);
                    conn.setConnectTimeout(15000);
                    conn.setRequestProperty("User-Agent", "RH-Terminal/Android");

                    for (String[] h : headers) {
                        conn.setRequestProperty(h[0], h[1]);
                    }

                    if (data != null) {
                        conn.setDoOutput(true);
                        OutputStream os = conn.getOutputStream();
                        os.write(data.getBytes());
                        os.flush();
                        os.close();
                    }

                    int code = conn.getResponseCode();
                    InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();

                    if (outFile != null) {
                        File f = new File(activity.currentDir, outFile);
                        FileOutputStream fos = new FileOutputStream(f);
                        byte[] buf = new byte[4096]; int len;
                        while ((len = is.read(buf)) > 0) fos.write(buf, 0, len);
                        fos.close(); is.close();
                        logOnUi("Success: file berhasil didownload ke " + f.getName());
                    } else {
                        BufferedReader in = new BufferedReader(new InputStreamReader(is));
                        StringBuilder response = new StringBuilder(); String line;
                        while ((line = in.readLine()) != null) response.append(line);
                        in.close();
                        final String res = response.toString();
                        activity.runOnUiThread(new Runnable() {
                            public void run() {
                                try { activity.appendRawHtml(activity.formatJsonToTable(res)); }
                                catch (Exception e) { activity.formatAndAppendLog(res); }
                            }
                        });
                    }
                } catch (Exception e) {
                    logOnUi("Error: " + e.getMessage());
                }
            }
        }).start();
    }

    private void logOnUi(final String msg) {
        activity.runOnUiThread(new Runnable() { public void run() { activity.formatAndAppendLog(msg); }});
    }

    private void logOnUiTab(final String msg, final int tabIndex) {
        activity.runOnUiThread(new Runnable() {
            public void run() { activity.appendHtmlToTab(msg, tabIndex); }
        });
    }

    public void runSSH(final String u, final String p, final String h, final String c) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSch jsch = new JSch();
                    Session s = jsch.getSession(u, h, 22);
                    s.setPassword(p);
                    java.util.Properties cfg = new java.util.Properties();
                    cfg.put("StrictHostKeyChecking", "no");
                    s.setConfig(cfg);
                    logOnUi("Connecting SSH...");
                    s.connect(10000);
                    ChannelExec ch = (ChannelExec) s.openChannel("exec");
                    ch.setCommand(c); ch.setInputStream(null); ch.setErrStream(System.err);
                    BufferedReader in = new BufferedReader(new InputStreamReader(ch.getInputStream()));
                    ch.connect();
                    String l; while ((l = in.readLine()) != null) {
                        final String f = l; logOnUi(f);
                    }
                    ch.disconnect(); s.disconnect();
                    logOnUi("SSH Closed.");
                } catch (Exception e) {
                    logOnUi("Error SSH: " + e.getMessage());
                }
            }
        }).start();
    }

    private String getMimeType(String path) {
        String p = path.toLowerCase();
        if (p.endsWith(".html") || p.endsWith(".htm")) return "text/html";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".js")) return "application/javascript";
        if (p.endsWith(".json")) return "application/json";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".jpg") || p.endsWith(".jpeg")) return "image/jpeg";
        if (p.endsWith(".gif")) return "image/gif";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".ico")) return "image/x-icon";
        if (p.endsWith(".mp3")) return "audio/mpeg";
        if (p.endsWith(".wav")) return "audio/wav";
        if (p.endsWith(".ogg")) return "audio/ogg";
        if (p.endsWith(".mp4")) return "video/mp4";
        if (p.endsWith(".webm")) return "video/webm";
        if (p.endsWith(".txt")) return "text/plain";
        if (p.endsWith(".pdf")) return "application/pdf";
        if (p.endsWith(".zip")) return "application/zip";
        return "application/octet-stream";
    }

    public void startLiveServer(final String targetPath, final int port, final boolean isDebug, final int tabIndex) {
        if (isServerRunning) stopLiveServer();
        isServerRunning = true;

        final File target = new File(targetPath);
        if (!target.exists() || target.isDirectory()) {
            logOnUiTab("<font color='#FF5555'>error: file ga ketemu atau itu folder.</font>", tabIndex);
            isServerRunning = false;
            return;
        }

        final File baseDir = target.getParentFile();
        final String initialFileName = target.getName();

        logOnUiTab("<font color='#00FF00'>menjalankan live server di port " + port + "...</font><br>buka browser lu: <font color='#00FFFF'>http://localhost:" + port + "</font>", tabIndex);
        if (isDebug) logOnUiTab("<font color='#FFFF00'>[debug mode] eruda & error catcher aktif.</font>", tabIndex);

        new Thread(new Runnable() {
            public void run() {
                try {
                    serverSocket = new ServerSocket(port);
                    while (isServerRunning) {
                        final Socket client = serverSocket.accept();
                        new Thread(new Runnable() {
                            public void run() { handleLiveClient(client, target, baseDir, initialFileName, isDebug, tabIndex); }
                        }).start();
                    }
                } catch (Exception e) {
                    if (isServerRunning) logOnUiTab("<font color='#FF5555'>server crash: " + e.getMessage() + "</font>", tabIndex);
                }
            }
        }).start();
    }

    public void stopLiveServer() {
        isServerRunning = false;
        try { if(serverSocket != null) serverSocket.close(); } catch(Exception e){}
        logOnUi("Live Server Stopped.");
    }

    private void handleLiveClient(Socket client, File targetHtml, File baseDir, String initialFileName, boolean isDebug, int tabIndex) {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream()));
            OutputStream outStream = client.getOutputStream();
            PrintWriter out = new PrintWriter(outStream, true);

            String requestLine = in.readLine();
            if (requestLine != null && requestLine.startsWith("GET")) {
                String[] parts = requestLine.split(" ");
                if (parts.length > 1) {
                    String fullPath = parts[1];
                    String reqPath = fullPath;
                    String query = "";

                    if (fullPath.contains("?")) {
                        reqPath = fullPath.substring(0, fullPath.indexOf("?"));
                        query = fullPath.substring(fullPath.indexOf("?") + 1);
                    }

                    if (reqPath.equals("/_terminal_log")) {
                        String errMsg = "unknown error";
                        if (query.startsWith("err=")) errMsg = URLDecoder.decode(query.substring(4), "UTF-8");
                        logOnUiTab("<font color='#FF5555'>[js log] " + errMsg + "</font>", tabIndex);
                        out.print("HTTP/1.1 200 OK\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n");
                        out.flush();
                        client.close();
                        return;
                    }

                    if (reqPath.equals("/_eruda.js")) {
                        serveAsset(out, outStream, "eruda.js", "application/javascript");
                        client.close();
                        return;
                    }

                    File fileToServe;
                    boolean injectHtml = false;

                    if (reqPath.equals("/") || reqPath.equals("/" + initialFileName)) {
                        fileToServe = targetHtml;
                        if (isDebug) injectHtml = true;
                    } else {
                        reqPath = URLDecoder.decode(reqPath, "UTF-8");
                        if (reqPath.startsWith("/")) reqPath = reqPath.substring(1);
                        fileToServe = new File(baseDir, reqPath);
                    }

                    if (fileToServe.exists() && !fileToServe.isDirectory()) {
                        String mime = getMimeType(fileToServe.getName());
                        out.print("HTTP/1.1 200 OK\r\n");
                        out.print("Content-Type: " + mime + "\r\n");
                        out.print("Access-Control-Allow-Origin: *\r\n");
                        out.print("Connection: close\r\n\r\n");
                        out.flush();

                        if (injectHtml) {
                            BufferedReader br = new BufferedReader(new FileReader(fileToServe));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = br.readLine()) != null) sb.append(line).append("\n");
                            br.close();

                            String htmlStr = sb.toString();
                            String injectScript = "\n<script src=\"/_eruda.js\"></script>\n" +
                                "<script>\n" +
                                "eruda.init();\n" +
                                "window.onerror = function(msg, url, line) {\n" +
                                "  fetch('/_terminal_log?err=' + encodeURIComponent(msg + ' (baris ' + line + ')'));\n" +
                                "  return false;\n" +
                                "};\n" +
                                "const oriErr = console.error;\n" +
                                "console.error = function(...args) {\n" +
                                "  fetch('/_terminal_log?err=' + encodeURIComponent('console error: ' + args.join(' ')));\n" +
                                "  oriErr.apply(console, args);\n" +
                                "};\n" +
                                "</script>\n";

                            if (htmlStr.contains("</body>")) htmlStr = htmlStr.replace("</body>", injectScript + "</body>");
                            else if (htmlStr.contains("</head>")) htmlStr = htmlStr.replace("</head>", injectScript + "</head>");
                            else htmlStr += injectScript;

                            outStream.write(htmlStr.getBytes("UTF-8"));
                        } else {
                            FileInputStream fis = new FileInputStream(fileToServe);
                            byte[] buffer = new byte[8192];
                            int len;
                            while ((len = fis.read(buffer)) != -1) outStream.write(buffer, 0, len);
                            fis.close();
                        }
                        outStream.flush();
                    } else {
                        out.print("HTTP/1.1 404 Not Found\r\nContent-Type: text/plain\r\n\r\n404 bro, file ga ketemu: " + reqPath);
                        out.flush();
                    }
                }
            }
            client.close();
        } catch (Exception e) {}
    }

    private void serveAsset(PrintWriter out, OutputStream outStream, String assetName, String mime) {
        try {
            out.print("HTTP/1.1 200 OK\r\nContent-Type: " + mime + "\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n");
            out.flush();
            InputStream is = activity.getAssets().open(assetName);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) outStream.write(buffer, 0, len);
            is.close();
            outStream.flush();
        } catch (Exception e) {
            out.print("HTTP/1.1 404 Not Found\r\n\r\nAsset ga ketemu di apk lu bro.");
            out.flush();
        }
    }
}
