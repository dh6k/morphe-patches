package app.morphe.extension.helium;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Extension-process discriminator matching upstream Titanium commit ff12f1c
 * ("ext: priority"): only children launched with {@code --extension-process}
 * receive IMPORTANT importance / STRONG binding. Renderer and GPU children
 * keep stock values so RAM and battery pressure stay bounded.
 */
public final class HeliumProcessBoost {
    public static final String EXTENSION_PROCESS_SWITCH = "--extension-process";
    /** ChildProcessImportance.IMPORTANT. */
    public static final int IMPORTANT = 3;
    /** Chromium ChildBindingState.STRONG. */
    public static final int STRONG_BINDING = 4;

    private static final Map<Object, Boolean> EXTENSION_HELPERS =
            Collections.synchronizedMap(new WeakHashMap<Object, Boolean>());

    /**
     * Command line of the spawn currently being created. Launcher thread runs
     * one createAndStart at a time, so a single slot is enough.
     */
    private static String[] spawnCommandLine;

    private HeliumProcessBoost() {}

    public static boolean isExtensionProcess(String[] commandLine) {
        return commandLine != null && Arrays.asList(commandLine).contains(EXTENSION_PROCESS_SWITCH);
    }

    public static void setSpawnCommandLine(String[] commandLine) {
        spawnCommandLine = commandLine;
    }

    /** Records whether {@code helper} is an extension process, using the in-flight command line. */
    public static void noteHelper(Object helper) {
        if (helper != null && isExtensionProcess(spawnCommandLine)) {
            EXTENSION_HELPERS.put(helper, Boolean.TRUE);
        }
    }

    public static boolean isExtension(Object helper) {
        return helper != null && EXTENSION_HELPERS.containsKey(helper);
    }

    /** {@link #STRONG_BINDING} for the in-flight extension spawn; otherwise keeps {@code original}. */
    public static int scopedBinding(int original) {
        return isExtensionProcess(spawnCommandLine) ? STRONG_BINDING : original;
    }

    /** {@link #IMPORTANT} for extension helpers; otherwise keeps {@code original}. */
    public static int scopedImportance(Object helper, int original) {
        return isExtension(helper) ? IMPORTANT : original;
    }
}
