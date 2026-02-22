package rh.system.shell;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.io.*;
import java.util.ArrayList;

public class StorageHelper {

    private MainActivity activity;
    private final String SAVE_FILE = "terminal_v16_final.dat";

    public StorageHelper(MainActivity activity) {
        this.activity = activity;
    }


    public boolean safRename(String fullPath, String newName) {
        try {
            if (!fullPath.startsWith("/storage/")) return false;
            String[] parts = fullPath.split("/");
            if (parts.length < 4) return false;
            String sdId = parts[2];
            String relativePath = fullPath.substring(fullPath.indexOf(sdId) + sdId.length() + 1).replace("/", "%2F").replace(" ", "%20");
            String uriString = "content://com.android.externalstorage.documents/tree/" + sdId + "%3A/document/" + sdId + "%3A" + relativePath;
            Uri fileUri = Uri.parse(uriString);
            return (DocumentsContract.renameDocument(activity.getContentResolver(), fileUri, newName) != null);
        } catch (Exception e) { return false; }
    }

    public boolean safMove(String srcPath, String destDirPath) {
        InputStream in = null; OutputStream out = null;
        try {
            if (!srcPath.startsWith("/storage/") || !destDirPath.startsWith("/storage/")) return false;

            String[] srcParts = srcPath.split("/"); String srcId = srcParts[2];
            String srcRel = srcPath.substring(srcPath.indexOf(srcId) + srcId.length() + 1).replace("/", "%2F").replace(" ", "%20");
            Uri srcUri = Uri.parse("content://com.android.externalstorage.documents/tree/" + srcId + "%3A/document/" + srcId + "%3A" + srcRel);

            String[] destParts = destDirPath.split("/"); String destId = destParts[2];
            if (destDirPath.endsWith("/")) destDirPath = destDirPath.substring(0, destDirPath.length() - 1);
            String destRel = destDirPath.substring(destDirPath.indexOf(destId) + destId.length() + 1).replace("/", "%2F").replace(" ", "%20");
            Uri destDirUri = Uri.parse("content://com.android.externalstorage.documents/tree/" + destId + "%3A/document/" + destId + "%3A" + destRel);

            String fileName = new File(srcPath).getName();
            Uri newFileUri = DocumentsContract.createDocument(activity.getContentResolver(), destDirUri, "application/octet-stream", fileName);
            if (newFileUri == null) return false;

            in = activity.getContentResolver().openInputStream(srcUri);
            out = activity.getContentResolver().openOutputStream(newFileUri);
            byte[] buffer = new byte[4096]; int len;
            while ((len = in.read(buffer)) > 0) out.write(buffer, 0, len);
            in.close(); out.close();

            DocumentsContract.deleteDocument(activity.getContentResolver(), srcUri);
            return true;
        } catch (Exception e) { 
            try { if(in!=null) in.close(); if(out!=null) out.close(); } catch(Exception ex){}
            return false; 
        }
    }


    public void saveSessions() {
        try {
            StringBuilder sb = new StringBuilder();
            for(int i=0; i<activity.tabData.size(); i++) {
                String safeContent = activity.tabData.get(i).toString().replace("||SPLIT||", "");
                String safeName = activity.tabNames.get(i).replace("[#####]", "");
                sb.append(safeName).append("[#####]").append(safeContent).append("||SPLIT||");
            }
            FileOutputStream fos = activity.openFileOutput(SAVE_FILE, Context.MODE_PRIVATE);
            fos.write(sb.toString().getBytes());
            fos.close();
        } catch (Exception e) {}
    }

    public void loadSessions() {
        activity.tabData.clear();
        activity.tabNames.clear();
        try {
            File file = new File(activity.getFilesDir(), SAVE_FILE);
            if(file.exists()) {
                FileInputStream fis = activity.openFileInput(SAVE_FILE);
                BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
                StringBuilder sb = new StringBuilder();
                String line;
                while((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                String[] sessions = sb.toString().split("\\|\\|SPLIT\\|\\|");
                for(String s : sessions) {
                    if(!s.isEmpty()) {
                        if (s.contains("[#####]")) {
                            String[] parts = s.split("\\[#####\\]");
                            if (parts.length >= 2) {
                                activity.tabNames.add(parts[0]);
                                activity.tabData.add(new StringBuilder(parts[1]));
                            } else {
                                activity.tabNames.add("SESI " + (activity.tabNames.size() + 1));
                                activity.tabData.add(new StringBuilder(s));
                            }
                        } else {
                            activity.tabNames.add("SESI " + (activity.tabNames.size() + 1));
                            activity.tabData.add(new StringBuilder(s));
                        }
                    }
                }
            }
        } catch (Exception e) {}

        if(activity.tabData.isEmpty()) {
            activity.tabData.add(new StringBuilder("<b>RH Terminal v16.0</b><br>"));
            activity.tabNames.add("SESI 1");
        }
        activity.switchTab(0);
    }
}
