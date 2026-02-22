package rh.system.shell;

import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;

public class FileManager {

    private MainActivity activity;
    
    private List<String> imgExt = Arrays.asList(".jpg", ".jpeg", ".png", ".webp", ".gif", ".bmp");
    private List<String> vidExt = Arrays.asList(".mp4", ".mkv", ".avi", ".3gp", ".flv");
    private List<String> audExt = Arrays.asList(".mp3", ".wav", ".ogg", ".m4a", ".flac");
    private List<String> arcExt = Arrays.asList(".zip", ".rar", ".7z", ".tar", ".gz", ".jar", ".iso");

    public FileManager(MainActivity activity) {
        this.activity = activity;
    }

    public void execute(final String[] args) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (args.length < 2) {
                    log("Usage: file [rename/destination] [args...]");
                    return;
                }

                String subCmd = args[1];

                if (subCmd.equals("rename")) {
                    handleRename(args);
                } else if (subCmd.equals("destination")) {
                    handleDestination(args);
                } else {
                    log("Unknown subcommand: " + subCmd);
                }
            }
        }).start();
    }

    private void handleRename(String[] args) {
        String targetPath = "";
        String pattern = "";
        String type = "";
        boolean recursive = false;

        try {
            targetPath = args[2];
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-n") && i+1 < args.length) pattern = args[i+1];
                if (args[i].equals("-f") && i+1 < args.length) type = args[i+1];
                if (args[i].equals("-r")) recursive = true;
            }
        } catch (Exception e) {
            log("Format: file rename [dir] -n [pola] -f [tipe] (-r)");
            return;
        }

        if (targetPath.isEmpty() || pattern.isEmpty() || type.isEmpty()) {
            log("Error: Parameter kurang lengkap!");
            return;
        }

        File root = new File(targetPath);
        if (!root.exists()) { log("Folder tidak ditemukan."); return; }

        log("🚀 Mulai Rename di: " + targetPath);
        processRenameScan(root, pattern, type.toLowerCase(), recursive);
        log("✅ Rename Selesai.");
    }

    private int countNB = 1;

    private void processRenameScan(File dir, String pattern, String type, boolean recursive) {
        countNB = 1;
        File[] files = dir.listFiles();
        if (files == null) return;
        Arrays.sort(files);

        for (File f : files) {
            if (f.isDirectory()) {
                if (recursive) processRenameScan(f, pattern, type, recursive);
            } else {
                doRenameFile(f, pattern, type);
            }
        }
    }

    private void doRenameFile(File f, String pattern, String type) {
        String name = f.getName();
        String ext = getExtension(name);
        
        boolean match = false;
        if (type.equals("all")) match = true;
        else if (type.equals("image") && imgExt.contains(ext)) match = true;
        else if (type.equals("video") && vidExt.contains(ext)) match = true;
        else if (type.equals("audio") && audExt.contains(ext)) match = true;
        else if (type.startsWith("[") && type.endsWith("]")) {
            String custom = type.replace("[", "").replace("]", "");
            if (!custom.startsWith(".")) custom = "." + custom;
            if (ext.equals(custom)) match = true;
        }

        if (match) {
            String newName = pattern.replace("%", " ");
            
            if (newName.contains("{date}")) {
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
                newName = newName.replace("{date}", sdf.format(new Date(f.lastModified())));
            }
            if (newName.contains("{name}")) {
                String ori = name.substring(0, name.lastIndexOf("."));
                newName = newName.replace("{name}", ori);
            }
            if (newName.contains("{nb}")) newName = newName.replace("{nb}", String.valueOf(countNB));
            if (newName.contains("{nb3}")) newName = newName.replace("{nb3}", String.format("%03d", countNB));

            if (newName.contains("{t}")) {
                newName = newName.replace("{t}", ext.replace(".", ""));
            } else if (!newName.toLowerCase().endsWith(ext)) {
                newName += ext;
            }

            if (pattern.contains("{nb}")) countNB++;

            File dest = new File(f.getParentFile(), newName);
            boolean success = f.renameTo(dest);
            
            if (!success) {
                success = activity.safRename(f.getAbsolutePath(), newName);
            }

            if (success) {
                log("<font color='#00FF00'>✓ " + name + " -> " + newName + "</font>");
            } else {
                log("<font color='#FF5555'>✗ Gagal: " + name + " (Permission Error)</font>");
            }
        }
    }

    private void handleDestination(String[] args) {
        String srcPath = "";
        String destPath = "";
        String typeParam = "";

        try {
            srcPath = args[2];
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("-t") && i+1 < args.length) typeParam = args[i+1].toLowerCase();
                if (args[i].equals("-d") && i+1 < args.length) destPath = args[i+1];
            }
        } catch (Exception e) {
            log("Format: file destination [src] -t [tipe] -d [dest]");
            return;
        }

        File srcDir = new File(srcPath);
        File destDir = new File(destPath);

        if (!srcDir.exists()) { log("Source folder not found."); return; }
        if (!destDir.exists()) destDir.mkdirs();

        log("📦 Moving files from: " + srcPath + " to " + destPath);
        processMoveRecursive(srcDir, destDir, typeParam);
        log("✅ Move Selesai.");
    }

    private void processMoveRecursive(File dir, File destDir, String type) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                if (!f.getAbsolutePath().equals(destDir.getAbsolutePath())) {
                    processMoveRecursive(f, destDir, type);
                }
            } else {
                String ext = getExtension(f.getName());
                boolean match = false;
                
                if (type.equals("image") && imgExt.contains(ext)) match = true;
                else if (type.equals("video") && vidExt.contains(ext)) match = true;
                else if (type.equals("audio") && audExt.contains(ext)) match = true;
                else if (type.equals("archive") && arcExt.contains(ext)) match = true;
                else if (type.startsWith("[") && type.endsWith("]")) {
                    String custom = type.replace("[", "").replace("]", "");
                    if (!custom.startsWith(".")) custom = "." + custom;
                    if (ext.equals(custom)) match = true;
                }

                if (match) {
                    moveFileSafe(f, destDir);
                }
            }
        }
    }

    private void moveFileSafe(File source, File destDir) {
        File target = new File(destDir, source.getName());
        
        int c = 1;
        while (target.exists()) {
            String name = source.getName();
            String base = name.substring(0, name.lastIndexOf("."));
            String ex = name.substring(name.lastIndexOf("."));
            target = new File(destDir, base + "_" + c + ex);
            c++;
        }

        boolean moved = source.renameTo(target);

        if (!moved) {
            moved = activity.safMove(source.getAbsolutePath(), destDir.getAbsolutePath());
        }

        if (moved) {
            log("<font color='#00FF00'>✓ Moved: " + source.getName() + "</font>");
        } else {
            log("<font color='#FF5555'>✗ Fail: " + source.getName() + "</font>");
        }
    }

    private String getExtension(String name) {
        int i = name.lastIndexOf(".");
        return (i > 0) ? name.substring(i).toLowerCase() : "";
    }

    private void log(final String msg) {
        activity.runOnUiThread(new Runnable() {
            public void run() { activity.formatAndAppendLog(msg); }
        });
    }
}
