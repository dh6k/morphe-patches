package app.morphe.extension.helium;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public final class HeliumKeepAliveStarter {
    private static final String TAG = "HeliumKeepAlive";
    private HeliumKeepAliveStarter() {}
    public static void start(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context, HeliumProcessKeepAliveService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent); else context.startService(intent);
        } catch (SecurityException e) {
            Log.w(TAG, "Foreground service denied by security policy", e);
        } catch (IllegalStateException e) {
            String reason = e.getClass().getName().contains("ForegroundServiceStartNotAllowedException")
                    ? "Foreground service start not allowed while app is backgrounded"
                    : "Foreground service start rejected by app state";
            Log.w(TAG, reason, e);
        }
    }
}
