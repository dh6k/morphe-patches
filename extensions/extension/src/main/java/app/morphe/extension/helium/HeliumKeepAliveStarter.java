package app.morphe.extension.helium;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public final class HeliumKeepAliveStarter {
    private static final String TAG = "HeliumKeepAlive";
    // ponytail: onStart fires on every activity show; one IPC per show wastes wakeups — start once per process, re-arm on destroy.
    private static volatile boolean started;
    private HeliumKeepAliveStarter() {}
    static synchronized void onServiceDestroyed() {
        started = false;
    }
    public static synchronized void start(Context context) {
        if (context == null || started) return;
        started = true;
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        Intent intent = new Intent(app, HeliumProcessKeepAliveService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) app.startForegroundService(intent); else app.startService(intent);
        } catch (SecurityException e) {
            started = false;
            Log.w(TAG, "Foreground service denied by security policy", e);
        } catch (IllegalStateException e) {
            started = false;
            String reason = e.getClass().getName().contains("ForegroundServiceStartNotAllowedException")
                    ? "Foreground service start not allowed while app is backgrounded"
                    : "Foreground service start rejected by app state";
            Log.w(TAG, reason, e);
        }
    }
}
