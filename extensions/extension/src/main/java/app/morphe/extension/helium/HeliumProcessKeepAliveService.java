package app.morphe.extension.helium;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

public final class HeliumProcessKeepAliveService extends Service {
    public static final String CHANNEL_ID = "helium_extension_runtime";
    public static final String SILENT_CHANNEL_ID = "helium_extension_runtime_silent";
    public static final int NOTIFICATION_ID = 0x48454c;

    private static final String TAG = "HeliumKeepAlive";
    private static final String META_ENABLED = "app.morphe.extension.helium.NOTIFICATION_ENABLED";
    private static final String META_TITLE = "app.morphe.extension.helium.NOTIFICATION_TITLE";
    private static final String META_TEXT = "app.morphe.extension.helium.NOTIFICATION_TEXT";
    private static final String DEFAULT_TITLE = "Titanium process protection active";
    private static final String DEFAULT_TEXT = "Reduces likelihood of extension runtime reclaim";

    private boolean foregroundStarted;

    private static String line(Bundle meta, String key, String fallback) {
        String value = meta != null ? meta.getString(key) : null;
        if (value == null) return fallback;
        value = value.trim();
        return value.isEmpty() ? fallback : value;
    }

    private synchronized boolean promote() {
        if (foregroundStarted) {
            return true;
        }

        try {
            boolean show = true;
            String title = DEFAULT_TITLE;
            String text = DEFAULT_TEXT;
            try {
                ComponentName component = new ComponentName(this, getClass());
                ServiceInfo info = getPackageManager().getServiceInfo(component, PackageManager.GET_META_DATA);
                Bundle meta = info.metaData;
                if (meta != null) {
                    show = meta.getBoolean(META_ENABLED, true);
                    title = line(meta, META_TITLE, DEFAULT_TITLE);
                    text = line(meta, META_TEXT, DEFAULT_TEXT);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Service info unavailable, using default notification", e);
            }

            if (Build.VERSION.SDK_INT >= 26) {
                NotificationManager manager =
                        (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                if (manager == null) {
                    Log.w(TAG, "Notification manager unavailable");
                    return false;
                }

                // ponytail: toggle only swaps to an IMPORTANCE_NONE channel; startForeground
                // still runs so keep-alive strength is identical whether shown or not.
                String channelId = show ? CHANNEL_ID : SILENT_CHANNEL_ID;
                int importance = show ? NotificationManager.IMPORTANCE_LOW : NotificationManager.IMPORTANCE_NONE;
                NotificationChannel channel = new NotificationChannel(channelId, title, importance);
                channel.setSound(null, null);
                channel.enableVibration(false);
                channel.setShowBadge(false);
                manager.createNotificationChannel(channel);
                manager.deleteNotificationChannel(show ? SILENT_CHANNEL_ID : CHANNEL_ID);

                startForeground(
                        NOTIFICATION_ID,
                        new Notification.Builder(this, channelId)
                                .setContentTitle(title)
                                .setContentText(text)
                                .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                                .setOngoing(true)
                                .setLocalOnly(true)
                                .setCategory(Notification.CATEGORY_SERVICE)
                                .build());
            } else {
                startForeground(
                        NOTIFICATION_ID,
                        new Notification.Builder(this)
                                .setContentTitle(title)
                                .setContentText(text)
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

    @Override
    public void onDestroy() {
        HeliumKeepAliveStarter.onServiceDestroyed();
        super.onDestroy();
    }
}
