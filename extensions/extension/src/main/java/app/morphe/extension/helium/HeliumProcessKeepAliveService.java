package app.morphe.extension.helium;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public final class HeliumProcessKeepAliveService extends Service {
    public static final String CHANNEL_ID = "helium_extension_runtime";
    public static final int NOTIFICATION_ID = 0x48454c;
    private static final String TITLE = "Helium background runtime";
    private static final String TEXT = "Keeps browser extension background runtime available";

    private void promote() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, TITLE, NotificationManager.IMPORTANCE_LOW);
            c.setSound(null, null); c.enableVibration(false); c.setShowBadge(false);
            ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
            startForeground(NOTIFICATION_ID, new Notification.Builder(this, CHANNEL_ID)
                    .setContentTitle(TITLE).setContentText(TEXT).setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                    .setOngoing(true).setLocalOnly(true).setCategory(Notification.CATEGORY_SERVICE).build());
        } else {
            startForeground(NOTIFICATION_ID, new Notification.Builder(this).setContentTitle(TITLE).setContentText(TEXT)
                    .setSmallIcon(android.R.drawable.stat_notify_sync_noanim).setOngoing(true).setLocalOnly(true).build());
        }
    }

    @Override public void onCreate() { super.onCreate(); promote(); }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { promote(); return START_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
}
