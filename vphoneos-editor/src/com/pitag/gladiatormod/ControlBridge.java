package com.pitag.gladiatormod;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

final class ControlBridge {
    private static final Uri URI = Uri.parse("content://com.pitag.gladiatormod.android7.control/state");
    private ControlBridge() {}

    static Bundle read(Context context) {
        try {
            ContentResolver resolver = context.getContentResolver();
            Bundle value = resolver.call(URI, "get", null, null);
            return value == null ? new Bundle() : value;
        } catch (Throwable ignored) { return new Bundle(); }
    }

    static void setEnabled(Context context, boolean enabled) {
        Bundle extras = new Bundle();
        extras.putBoolean("enabled", enabled);
        try { context.getContentResolver().call(URI, "enable", null, extras); }
        catch (Throwable ignored) { }
    }

    static boolean report(Context context, boolean hooked, boolean running, boolean ready, int slot) {
        Bundle extras = new Bundle();
        extras.putBoolean("hooked", hooked);
        extras.putBoolean("running", running);
        extras.putBoolean("save_ready", ready);
        extras.putInt("slot", slot);
        try {
            Bundle result = context.getContentResolver().call(URI, "status", null, extras);
            return result != null && result.getBoolean("enabled", false);
        } catch (Throwable ignored) { return false; }
    }
}
