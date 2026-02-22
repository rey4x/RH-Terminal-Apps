package rh.system.shell;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.json.JSONObject;
import bsh.Interpreter;
import android.os.Environment;

import javax.net.ssl.*;
import java.security.cert.X509Certificate;

public class AddonHelper {

    private MainActivity activity;
    private String ADDON_DIR;

    public HashMap<String, JSONObject> installedAddons = new HashMap<>();
    public HashMap<String, String> addonPaths = new HashMap<>(); 

    public AddonHelper(MainActivity activity, String ignoredPath) {
        this.activity = activity;
        this.ADDON_DIR = Environment.getExternalStorageDirectory().getAbsolutePath() + "/terminal/addons";
        new File(ADDON_DIR).mkdirs();
        loadInstalledAddons(); 
    }

    public void loadInstalledAddons() {
        installedAddons.clear();
        addonPaths.clear();
        File dir = new File(ADDON_DIR);
        if (!dir.exists()) return;

        File[] files = dir.listFiles();
        if (files != null) {
            for (File folder : files) {
                if (folder.isDirectory()) {
                    try {
                        File def = new File(folder, "definition.json");
                        if (def.exists()) {
                            String jsonContent = readFile(def);
                            JSONObject json = new JSONObject(jsonContent);

                            String rawArgs = json.optString("mainArgs", "$name");
                            String cmdName = rawArgs.equals("$name") ? folder.getName() : rawArgs.split(" ")[0];

                            installedAddons.put(cmdName, json);
                            addonPaths.put(cmdName, folder.getAbsolutePath());
                        }
                    } catch (Exception e) {}
                }
            }
        }
    }
    public void handlePkg(final String[] args) {
        new Thread(new Runnable() {
				@Override
				public void run() {
					if (args.length < 2) {
						log("Usage: pkg [install/list/remove]");
						return;
					}

					String action = args[1];

					if (action.equals("list")) {
						if (installedAddons.isEmpty()) {
							log("Belum ada addon terinstall.");
						} else {
							StringBuilder sb = new StringBuilder();
							sb.append("<br><b>INSTALLED PACKAGES:</b><br>");
							for (String key : installedAddons.keySet()) {
								JSONObject json = installedAddons.get(key);
								String ver = json.optString("version", "1.0");
								sb.append("<font color='#00FF00'>• ").append(key).append("</font>")
									.append(" (v").append(ver).append(")<br>");
							}
							log(sb.toString());
						}
					} 
					else if (action.equals("remove")) {
						if (args.length < 3) { log("Usage: pkg remove [nama_command]"); return; }
						String name = args[2];
						if (addonPaths.containsKey(name)) {
							File folder = new File(addonPaths.get(name));
							deleteRecursive(folder);
							loadInstalledAddons(); 
							log("Package '" + name + "' dihapus.");
						} else {
							log("Package tidak ditemukan.");
						}
					} 
					else if (action.equals("install") || isInstallVariant(args)) {
						String target = (action.equals("install")) ? args[2] : args[1]; 
						processInstall(target);
					}
					else {
						processInstall(args[1]); 
					}
				}
			}).start();
    }

    private boolean isInstallVariant(String[] args) {
        return args.length == 2 && !args[1].equals("list");
    }

    private void processInstall(String source) {
        File tempZip = null;
        try {
            File zipFile;

            if (source.startsWith("http")) {
                log("Downloading from URL...");
                String fileName = source.substring(source.lastIndexOf('/') + 1);
                if (fileName.isEmpty() || !fileName.endsWith(".zip")) fileName = "temp_addon.zip";

                tempZip = new File(activity.getCacheDir(), fileName);
                downloadFileUnsafe(source, tempZip);
                zipFile = tempZip;
            } 
            else if (source.startsWith("/")) {
                zipFile = new File(source);
            } 
            else {
                String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath() + "/terminal";
                zipFile = new File(baseDir, source);
                if (!zipFile.exists() && !source.endsWith(".zip")) {
                    zipFile = new File(baseDir, source + ".zip");
                }
            }

            if (!zipFile.exists()) {
                log("File tidak ditemukan: " + source);
                return;
            }

            installFromZip(zipFile);

        } catch (Exception e) {
            log("Install Error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            if (tempZip != null && tempZip.exists()) tempZip.delete();
        }
    }

    private void installFromZip(File zipFile) throws Exception {
        log("Unpacking package...");

        File tempExtractDir = new File(ADDON_DIR, "tmp_" + System.currentTimeMillis());
        if(tempExtractDir.exists()) deleteRecursive(tempExtractDir);
        tempExtractDir.mkdirs();

        java.util.zip.ZipFile zip = new java.util.zip.ZipFile(zipFile);
        java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            java.util.zip.ZipEntry entry = entries.nextElement();
            File destFile = new File(tempExtractDir, entry.getName());
            if (entry.isDirectory()) { 
                destFile.mkdirs(); 
            } else {
                destFile.getParentFile().mkdirs();
                InputStream is = zip.getInputStream(entry);
                FileOutputStream fos = new FileOutputStream(destFile);
                byte[] buffer = new byte[1024]; int len;
                while ((len = is.read(buffer)) > 0) fos.write(buffer, 0, len);
                fos.close(); is.close();
            }
        }
        zip.close();

        File defFile = new File(tempExtractDir, "definition.json");
        File addonRoot = tempExtractDir;

        if (!defFile.exists()) {
            File[] subs = tempExtractDir.listFiles();
            if (subs != null && subs.length == 1 && subs[0].isDirectory()) {
                File innerDef = new File(subs[0], "definition.json");
                if (innerDef.exists()) {
                    defFile = innerDef;
                    addonRoot = subs[0];
                }
            }
        }

        if (!defFile.exists()) {
            deleteRecursive(tempExtractDir);
            throw new Exception("definition.json tidak ditemukan dalam zip!");
        }

        String jsonContent = readFile(defFile);
        JSONObject json = new JSONObject(jsonContent);

        String rawArgs = json.optString("mainArgs", "$name");
        String finalFolderName;

        if (rawArgs.equals("$name")) {
            String zName = zipFile.getName();
            if (zName.contains(".")) zName = zName.substring(0, zName.lastIndexOf("."));
            finalFolderName = zName;
        } else {
            finalFolderName = rawArgs.split(" ")[0];
        }

        File finalDir = new File(ADDON_DIR, finalFolderName);
        if (finalDir.exists()) deleteRecursive(finalDir);

        boolean success = addonRoot.renameTo(finalDir);

        if (addonRoot.getParentFile().equals(tempExtractDir)) {
			deleteRecursive(tempExtractDir);
        } else if (!success) {
			deleteRecursive(tempExtractDir);
			throw new Exception("Gagal membuat folder addon: " + finalFolderName);
        }

        loadInstalledAddons();

        String icon = json.optString("icon", "");
        String desc = json.optString("description", "No Description");
        String ver = json.optString("version", "1.0");

        final String html = 
            "<br><img src='" + new File(finalDir, icon).getAbsolutePath() + "'><br>" +
            "<font color='#00FF00'><b>Successfully Installed!</b></font><br>" +
            "Folder: <font color='#FFFF00'>" + finalFolderName + "</font><br>" +
            "Ver: " + ver + "<br>" +
            "<i>" + desc + "</i><br>";

        activity.runOnUiThread(new Runnable() {
				public void run() { activity.updateLastLine(html); }
			});
    }

    public boolean executeAddon(final String cmdName, final String[] args) {
        if (!installedAddons.containsKey(cmdName)) return false;

        new Thread(new Runnable() {
				@Override
				public void run() {
					try {
						JSONObject json = installedAddons.get(cmdName);
						String path = addonPaths.get(cmdName);
						String script = json.getString("mainScript");

						File scriptFile = new File(path, script);

						Interpreter i = new Interpreter();
						i.set("context", activity);
						i.set("args", args);
						i.set("addonPath", path);
						
						final int targetTab = activity.currentTabIndex;
						i.set("targetTab", targetTab);

						i.eval("void print(String s) { String txt = s.replace(\"Error:\", \"<font color='#FF5555'>Error:</font>\").replace(\"Success:\", \"<font color='#00FF00'>Success:</font>\"); context.appendBackgroundSafe(\"<font color='#CCCCCC'>\" + txt + \"</font>\", targetTab); }");

						i.source(scriptFile.getAbsolutePath());

					} catch (Exception e) {
						log("Runtime Error: " + e.getMessage());
					}
				}
			}).start();
        return true;
    }

    private void downloadFileUnsafe(String urlStr, File dest) throws Exception {
        URL url = new URL(urlStr);
        URLConnection conn = url.openConnection();

        if (conn instanceof HttpsURLConnection) {
            TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    public java.security.cert.X509Certificate[] getAcceptedIssuers() { return null; }
                    public void checkClientTrusted(X509Certificate[] certs, String authType) {}
                    public void checkServerTrusted(X509Certificate[] certs, String authType) {}
                }
            };

            SSLContext sc = SSLContext.getInstance("TLS");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());

            HttpsURLConnection httpsConn = (HttpsURLConnection) conn;
            httpsConn.setSSLSocketFactory(sc.getSocketFactory());
            httpsConn.setHostnameVerifier(new HostnameVerifier() {
					public boolean verify(String hostname, SSLSession session) { return true; }
				});
        }

        conn.setConnectTimeout(30000); 
        conn.setReadTimeout(30000);

        InputStream in = conn.getInputStream();
        FileOutputStream out = new FileOutputStream(dest);
        byte[] buf = new byte[1024]; int len;
        while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        in.close(); out.close();
    }

    private String readFile(File f) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(f));
        StringBuilder sb = new StringBuilder(); String line;
        while ((line = br.readLine()) != null) sb.append(line);
        br.close();
        return sb.toString();
    }

    private void deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if(files != null) for (File c : files) deleteRecursive(c);
        }
        f.delete();
    }

    private void log(final String s) {
        activity.runOnUiThread(new Runnable() { public void run() { activity.formatAndAppendLog(s); }});
    }
}
