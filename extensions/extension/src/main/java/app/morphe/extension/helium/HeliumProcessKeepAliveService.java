package app.morphe.extension.helium;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

public final class HeliumProcessKeepAliveService extends Service {
    public static final String CHANNEL_ID = "helium_extension_runtime";
    public static final int NOTIFICATION_ID = 0x48454c;

    private static final String TAG = "HeliumKeepAlive";
    private static final String TITLE = "Helium extensions active";
    private static final String TEXT =
            "Reduces likelihood of browser extension runtime reclaim";

    private boolean foregroundStarted;

    private synchronized boolean promote() {
        if (foregroundStarted) {
            return true;
        }

        try {
            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager manager =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (manager == null) {
                    Log.w(TAG, "Notification manager unavailable");
                    return false;
                }

                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        TITLE,
                        NotificationManager.IMPORTANCE_LOW);
                channel.setSound(null, null);
                channel.enableVibration(false);
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);

                startForeground(
                        NOTIFICATION_ID,
                        new Notification.Builder(this, CHANNEL_ID)
                                .setContentTitle(TITLE)
                                .setContentText(TEXT)
                                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                                .setOngoing(true)
                                .setLocalOnly(true)
                                .setCategory(Notification.CATEGORY_SERVICE)
                                .build());
            } else {
                startForeground(
                        NOTIFICATION_ID,
                        new Notification.Builder(this)
                                .setContentTitle(TITLE)
                                .setContentText(TEXT)
                                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                                .setOngoing(true)
                                .setLocalOnly(true)
                                .build());
            }
            foregroundStarted = true;
            return true;
        } catch (RuntimeException exception) {
            Log.e(TAG, "Foreground notification setup failed", exception);
            return false;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        promote();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (promote()) {
            return START_STICKY;
        }
        stopSelf(startId);
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
