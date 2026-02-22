package rh.system.shell;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

public class TerminalService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Intent notifIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notifIntent, 0);

        int jumlahSesi = 0;
        if (MainActivity.tabData != null) {
            jumlahSesi = MainActivity.tabData.size();
        }

        Notification.Builder builder = new Notification.Builder(this)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("rh terminal")
            .setContentText(jumlahSesi + " sesi sedang berjalan di latar belakang")
            .setContentIntent(pendingIntent)
            .setOngoing(true);

        startForeground(1337, builder.build());

        return START_STICKY;
    }
}
