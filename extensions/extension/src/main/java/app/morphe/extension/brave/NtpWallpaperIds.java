package app.morphe.extension.brave;

import android.content.Context;
import android.content.res.Resources;

/**
 * Resolves the Morphe-installed NTP wallpaper drawable at runtime.
 * Lives in the extension so bytecode sites only need a 2-register
 * invoke-static — tight Chromium factories (e.g. 3-register ambient
 * catalog accessors) cannot host getIdentifier's 4-arg invoke.
 */
public final class NtpWallpaperIds {
    private static final String DRAWABLE = "morphe_custom_ntp_wallpaper";

    private NtpWallpaperIds() {}

    public static int drawableId() {
        try {
            Context app = currentApplication();
            if (app == null) return 0;
            Resources res = app.getResources();
            if (res == null) return 0;
            return res.getIdentifier(DRAWABLE, "drawable", app.getPackageName());
        } catch (Throwable t) {
            return 0;
        }
    }

    /** Hidden API; resolve reflectively so this class compiles against public SDK. */
    private static Context currentApplication() {
        try {
            Class<?> at = Class.forName("android.app.ActivityThread");
            Object app = at.getDeclaredMethod("currentApplication").invoke(null);
            return (Context) app;
        } catch (Throwable t) {
            return null;
        }
    }
}
