package rh.system.shell;

import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import java.io.File;

public class ProcessCommand {

    private MainActivity activity;

    public ProcessCommand(MainActivity activity) {
        this.activity = activity;
    }

    public void execute(String cmd) {
        if (!cmd.trim().isEmpty()) {
            activity.cmdHistory.add(cmd);
            activity.historyIndex = activity.cmdHistory.size();
        }

        String[] parts = cmd.trim().split("\\s+");
        String baseCmd = parts[0];
        final int taskTab = activity.currentTabIndex;

        if (activity.addonHelper.installedAddons.containsKey(baseCmd)) {
            activity.addonHelper.executeAddon(baseCmd, parts);
            return;
        }

        if (baseCmd.equals("cd")) {
            File target = null;
            if (parts.length == 1) {
                target = android.os.Environment.getExternalStorageDirectory();
            } 
            else if (parts[1].equals("..")) {
                if (activity.currentDir.getParentFile() != null) target = activity.currentDir.getParentFile();
            } 
            else {
                String path = cmd.substring(2).trim();
                if (path.startsWith("/")) target = new File(path);
                else target = new File(activity.currentDir, path);
            }

            if (target != null && target.exists() && target.isDirectory()) {
                activity.currentDir = target;
                activity.updatePromptUI();
            } else {
                activity.formatAndAppendLog("cd: folder not found: " + (parts.length > 1 ? parts[1] : ""));
            }
        }

        else if (baseCmd.equals("ls")) {
            File[] files = activity.currentDir.listFiles();
            StringBuilder sb = new StringBuilder();
            if (files != null) {
                java.util.ArrayList<File> dirs = new java.util.ArrayList<File>();
                java.util.ArrayList<File> fils = new java.util.ArrayList<File>();
                for (File f : files) {
                    if (f.isDirectory()) dirs.add(f);
                    else fils.add(f);
                }

                java.util.Collections.sort(dirs);
                java.util.Collections.sort(fils);

                sb.append("path: <font color='#00FFFF'>").append(activity.currentDir.getAbsolutePath()).append("</font><br>");

                for (File f : dirs) {
                    sb.append("<font color='#FFFF00'><b>").append(f.getName()).append("/</b></font><br>");
                }

                for (File f : fils) {
                    String name = f.getName().toLowerCase();
                    String color = "#FFFFFF";

                    if (name.endsWith(".mp4") || name.endsWith(".mkv") || name.endsWith(".avi") || name.endsWith(".3gp") || name.endsWith(".flv")) {
                        color = "#BA68C8";
                    } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp") || name.endsWith(".gif") || name.endsWith(".bmp")) {
                        color = "#FF00FF";
                    } else if (name.endsWith(".txt") || name.endsWith(".html") || name.endsWith(".css") || name.endsWith(".js") || name.endsWith(".json") || name.endsWith(".xml") || name.endsWith(".java") || name.endsWith(".bsh") || name.endsWith(".log") || name.endsWith(".csv") || name.endsWith(".md") || name.endsWith(".ini") || name.endsWith(".conf") || name.endsWith(".sh") || name.endsWith(".bat") || name.endsWith(".py")) {
						color = "#AAAAAA";
                    } else if (name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") || name.endsWith(".tar") || name.endsWith(".gz") || name.endsWith(".jar") || name.endsWith(".iso") || name.endsWith(".apk")) {
                        color = "#FFA500";
					} else if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg") || name.endsWith(".fsb") || name.endsWith(".opus") || name.endsWith(".aac") || name.endsWith(".flac") || name.endsWith(".m4a") || name.endsWith(".wma")) {
						color = "#ADD8E6";
                    }

                    sb.append("<font color='").append(color).append("'>").append(f.getName()).append("</font><br>");
                }
            } else {
                sb.append("<font color='#FF5555'>direktori kosong atau izin ditolak.</font><br>");
            }
            activity.appendHtmlToTab(sb.toString(), taskTab);
        }

        else if (baseCmd.equals("pkg")) {
            activity.addonHelper.handlePkg(parts);
        }

        else if (baseCmd.equals("ai")) {
            try {
                String provider = parts[1];
                String model = "";
                String key = "";
                for(int i=0; i<parts.length; i++) {
                    if (parts[i].equals("-m") && i+1 < parts.length) model = parts[i+1];
                    if (parts[i].equals("-k") && i+1 < parts.length) key = parts[i+1];
                }
                if (!model.isEmpty() && !key.isEmpty()) activity.aiHelper.setConfig(provider, model, key);
                else activity.formatAndAppendLog("Usage: ai [provider] -m [model] -k [apikey]");
            } catch(Exception e) {
                activity.formatAndAppendLog("Error format. Cth: ai gemini -m gemini-flash -k abc12345");
            }
        }

        else if (baseCmd.equals("gemini") || baseCmd.equals("chatgpt") || baseCmd.equals("deepseek")) {
            if (parts.length > 1) {
                String prompt = cmd.substring(baseCmd.length()).trim();
                activity.aiHelper.askAI(baseCmd, prompt, taskTab);
            } else activity.formatAndAppendLog("Ketik pertanyaan! Cth: " + baseCmd + " bikinin kode java");
        }

        else if (baseCmd.equals("file")) {
            activity.fileManager.execute(parts);
        }

        else if (baseCmd.equals("defcurl")) {
            if (parts.length >= 3) {
                activity.getSharedPreferences("TerminalMacros", android.content.Context.MODE_PRIVATE).edit().putString(parts[1], parts[2]).apply();
                activity.formatAndAppendLog("Saved: " + parts[1]);
            } else activity.formatAndAppendLog("Usage: defcurl <nama> <url>");
        }

        else if (baseCmd.equals("curl")) {
            if (parts.length >= 2) {
                String saved = activity.getSharedPreferences("TerminalMacros", android.content.Context.MODE_PRIVATE).getString(parts[1], null);
                if (saved != null) {
                    String url = saved;
                    int idx = 2;
                    while (url.contains("{}") && idx < parts.length) {
                        url = url.replaceFirst("\\{\\}", parts[idx++]);
                    }
                    activity.formatAndAppendLog("Exec: " + parts[1]);
                    activity.networkHelper.runCurl("curl " + url);
                } else {
                    activity.networkHelper.runCurl(cmd);
                }
            } else activity.formatAndAppendLog("Usage: curl <link> atau curl -X POST -H \"Auth: 123\" -d \"data\" <link>");
        }

		else if (baseCmd.equals("help")) {
            try {
                java.io.InputStream is = activity.getAssets().open("help.txt");
                java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(is));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();

                activity.appendHtmlToTab(sb.toString(), taskTab);
            } catch (Exception e) {
                activity.formatAndAppendLog("Error: File help.txt tidak ditemukan di assets.");
            }
        }

        else if (baseCmd.equals("ssh")) {
            if (parts.length >= 4) {
                StringBuilder args = new StringBuilder();
                for (int i = 4; i < parts.length; i++) args.append(parts[i]).append(" ");
                activity.networkHelper.runSSH(parts[1], parts[2], parts[3], args.toString());
            } else activity.formatAndAppendLog("Usage: ssh <user> <pass> <host> <cmd>");
        }

        else if (baseCmd.equals("live")) {
            String targetPath = "";
            int port = 8080;
            boolean isDebug = false;

            for (int i = 1; i < parts.length; i++) {
                if (parts[i].equals("--dir") && i + 1 < parts.length) {
                    targetPath = parts[++i];
                } else if (parts[i].equals("--port") && i + 1 < parts.length) {
                    try { port = Integer.parseInt(parts[++i]); } catch(Exception e){}
                } else if (parts[i].equals("--debug")) {
                    isDebug = true;
                }
            }

            if (targetPath.isEmpty()) {
                activity.formatAndAppendLog("<font color='#FF5555'>error: path file kosong. format: live --dir [file.html] --port [port] [--debug]</font>");
            } else {
                File target = new File(targetPath);
                if (!targetPath.startsWith("/")) {
                    target = new File(activity.currentDir, targetPath);
                }
                activity.networkHelper.startLiveServer(target.getAbsolutePath(), port, isDebug, taskTab);
            }
        }
        else if (cmd.equals("stop live")) {
            activity.networkHelper.stopLiveServer();
        }

        else if (baseCmd.equals("saf")) {
            try { activity.startActivity(new android.content.Intent(activity, SafActivity.class)); } catch(Exception e){}
        }
        else if (baseCmd.equals("clear")) {
            activity.tabData.set(activity.currentTabIndex, new StringBuilder());
            activity.tvOutput.setText("");
            activity.storageHelper.saveSessions();
        }

        else if (!cmd.trim().isEmpty()) {
            activity.runLocalCommand("cd " + activity.currentDir.getAbsolutePath() + " && " + cmd, taskTab);
        }
    
}
}
