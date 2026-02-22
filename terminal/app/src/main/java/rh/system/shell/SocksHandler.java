package rh.system.shell;

import com.jcraft.jsch.*;
import java.util.Properties;
import java.lang.reflect.Method;

public class SocksHandler {

    private Session session;
    private boolean isRunning = false;

    public interface SocksListener {
        void onLog(String message, String color);
    }

    public void start(final String user, final String pass, final String host, final int localPort, final SocksListener listener) {
        
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    JSch jsch = new JSch();
                    session = jsch.getSession(user, host, 22);
                    session.setPassword(pass);

                    Properties config = new Properties();
                    config.put("StrictHostKeyChecking", "no");
                    session.setConfig(config);

                    listener.onLog("Menghubungkan ke Server SSH...", "#FFFF00");
                    session.connect(15000); 

                    try {
                        Method method = session.getClass().getMethod("setPortForwardingD", int.class);
                        method.invoke(session, localPort);
                    } catch (Exception e1) {
                        try {
                            Method method2 = session.getClass().getMethod("setPortForwardingD", String.class, int.class);
                            method2.invoke(session, "127.0.0.1", localPort);
                        } catch (Exception e2) {
                            throw new Exception("Gagal mengaktifkan SOCKS: Metode tidak ditemukan di Library!");
                        }
                    }

                    listener.onLog("SUKSES! SOCKS Aktif di Port " + localPort, "#00FF00");
                    listener.onLog("Set Browser Proxy: 127.0.0.1:" + localPort, "#00FFFF");

                    isRunning = true;

                    while (isRunning && session.isConnected()) {
                        Thread.sleep(1000);
                    }

                } catch (Exception e) {
                    listener.onLog("SOCKS Error: " + e.getMessage(), "#FF0000");
                } finally {
                    stop();
                    if (isRunning) {
                        listener.onLog("SOCKS Terputus.", "#FF5555");
                    }
                }
            }
        }).start();
    }

    public void stop() {
        isRunning = false;
        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
}
