package app.morphe.extension.helium;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

public final class HeliumKeepAliveStarter {
    private HeliumKeepAliveStarter() {}
    public static void start(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context, HeliumProcessKeepAliveService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent); else context.startService(intent);
        } catch (SecurityException | IllegalStateException ignored) {
            // Foreground-start restrictions must not crash browser startup.
        }
    }
}
