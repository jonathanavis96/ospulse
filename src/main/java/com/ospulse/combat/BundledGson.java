package com.ospulse.combat;

import com.google.gson.Gson;

/**
 * Holder for the RuneLite client's injected {@link Gson}, seeded once at
 * plugin start-up so the static combat repositories can parse their bundled
 * JSON through the client's shared instance instead of constructing their own
 * (the Plugin Hub packager forbids fresh Gson instances in shipped code).
 *
 * <p>Tests that exercise the repositories outside the client seed this
 * themselves before the first repository load.
 */
public final class BundledGson {
    private static volatile Gson gson;

    private BundledGson() {
    }

    /** Seeds the shared instance; the plugin does this before any repository loads. */
    public static void set(Gson g) {
        gson = g;
    }

    /** The shared instance, or throws if nothing has seeded it yet. */
    public static Gson get() {
        Gson g = gson;
        if (g == null) {
            throw new IllegalStateException(
                "BundledGson not initialised - seed it (OSPulsePlugin.startUp or test setup) "
                    + "before the combat repositories load");
        }
        return g;
    }
}
