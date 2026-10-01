package com.pitag.gladiatormod;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Process;

/** Small, package-checked bridge between the module app and the injected game process. */
public final class ControlProvider extends ContentProvider {
    public static final String AUTHORITY = "com.pitag.gladiatormod.android7.control";
    public static final Uri URI = Uri.parse("content://" + AUTHORITY + "/state");
    private static final String PREFS = "gm_vm_module_control";
    private static final String PREF_ENABLED = "enabled";
    private static final String PREF_HOOKED = "hooked";
    private static final String PREF_RUNNING = "running";
    private static final String PREF_READY = "save_ready";
    private static final String PREF_SLOT = "slot";
    private static final String PREF_UPDATED = "updated";

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "vnd.android.cursor.item/vnd." + AUTHORITY + ".state"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (getContext() == null || !isAllowedCaller()) return new Bundle();
        Context context = getContext();
        android.content.SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        Bundle result = new Bundle();
        if ("get".equals(method)) {
            result.putBoolean(PREF_ENABLED, prefs.getBoolean(PREF_ENABLED, false));
            result.putBoolean(PREF_HOOKED, prefs.getBoolean(PREF_HOOKED, false));
            result.putBoolean(PREF_RUNNING, prefs.getBoolean(PREF_RUNNING, false));
            result.putBoolean(PREF_READY, prefs.getBoolean(PREF_READY, false));
            result.putInt(PREF_SLOT, prefs.getInt(PREF_SLOT, 0));
            result.putLong(PREF_UPDATED, prefs.getLong(PREF_UPDATED, 0L));
            return result;
        }
        if ("enable".equals(method)) {
            boolean enabled = extras != null && extras.getBoolean(PREF_ENABLED, false);
            prefs.edit().putBoolean(PREF_ENABLED, enabled).apply();
            result.putBoolean(PREF_ENABLED, enabled);
            context.getContentResolver().notifyChange(URI, null);
            return result;
        }
        if ("status".equals(method)) {
            boolean hooked = extras != null && extras.getBoolean(PREF_HOOKED, false);
            boolean running = extras != null && extras.getBoolean(PREF_RUNNING, false);
            boolean ready = extras != null && extras.getBoolean(PREF_READY, false);
            int slot = extras == null ? 0 : extras.getInt(PREF_SLOT, 0);
            prefs.edit()
                    .putBoolean(PREF_HOOKED, hooked)
                    .putBoolean(PREF_RUNNING, running)
                    .putBoolean(PREF_READY, ready)
                    .putInt(PREF_SLOT, slot)
                    .putLong(PREF_UPDATED, System.currentTimeMillis())
                    .apply();
            context.getContentResolver().notifyChange(URI, null);
            result.putBoolean(PREF_ENABLED, prefs.getBoolean(PREF_ENABLED, false));
            return result;
        }
        return result;
    }

    private boolean isAllowedCaller() {
        int uid = Binder.getCallingUid();
        if (uid == Process.myUid()) return true;
        Context context = getContext();
        if (context == null) return false;
        PackageManager pm = context.getPackageManager();
        String[] packages = pm.getPackagesForUid(uid);
        if (packages == null) return false;
        for (String name : packages) {
            if ("com.rene.gladiatormanager".equals(name)) return true;
        }
        return false;
    }
}
