package com.pitag.gladiatormod;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Loads only in Gladiator Manager and observes its local save/activity lifecycle. */
public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TARGET = "com.rene.gladiatormanager";
    private static final String APP_CLASS = TARGET + ".state.GladiatorApp";
    private static boolean installed;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!TARGET.equals(lpparam.packageName) || !TARGET.equals(lpparam.processName)) return;
        synchronized (HookEntry.class) {
            if (installed) return;
        }
        try {
            final Class<?> appClass = XposedHelpers.findClass(APP_CLASS, lpparam.classLoader);
            XposedHelpers.findAndHookMethod(appClass, "onCreate", new XC_MethodHook() {
            @Override protected void afterHookedMethod(XC_MethodHook.MethodHookParam param) {
                final Application app = (Application) param.thisObject;
                ControlBridge.report(app, true, false, false, 0);
                final Handler handler = new Handler(Looper.getMainLooper());
                final Activity[] active = new Activity[1];
                final Runnable[] heartbeat = new Runnable[1];
                heartbeat[0] = new Runnable() {
                    @Override public void run() {
                        Activity current = active[0];
                        if (current == null) return;
                        try { EditorOverlay.onResumed(current); }
                        catch (Throwable error) { XposedBridge.log("GM editor heartbeat: " + error); }
                        handler.postDelayed(this, 5000L);
                    }
                };
                app.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
                    @Override public void onActivityCreated(Activity activity, Bundle state) { }
                    @Override public void onActivityStarted(Activity activity) { }
                    @Override public void onActivityResumed(Activity activity) {
                        active[0] = activity;
                        handler.removeCallbacks(heartbeat[0]);
                        try { EditorOverlay.onResumed(activity); }
                        catch (Throwable error) { XposedBridge.log("GM editor resume hook: " + error); }
                        handler.postDelayed(heartbeat[0], 5000L);
                    }
                    @Override public void onActivityPaused(Activity activity) {
                        if (active[0] == activity) {
                            active[0] = null;
                            handler.removeCallbacks(heartbeat[0]);
                        }
                        try { EditorOverlay.onPaused(activity); }
                        catch (Throwable error) { XposedBridge.log("GM editor pause hook: " + error); }
                    }
                    @Override public void onActivityStopped(Activity activity) { }
                    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
                    @Override public void onActivityDestroyed(Activity activity) {
                        if (active[0] == activity) {
                            active[0] = null;
                            handler.removeCallbacks(heartbeat[0]);
                        }
                        try { EditorOverlay.detach(activity); }
                        catch (Throwable ignored) { }
                    }
                });
            }
            });
            synchronized (HookEntry.class) { installed = true; }
        } catch (Throwable error) {
            synchronized (HookEntry.class) { installed = false; }
            XposedBridge.log("GM editor could not hook Gladiator Manager startup: " + error);
        }
    }
}
