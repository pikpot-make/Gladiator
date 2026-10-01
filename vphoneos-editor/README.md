# VPhoneOS Android 7 editor module

Source snapshot for the Android 7.1.2 (API 25) Xposed module, package `com.pitag.gladiatormod.android7`, version `1.2-android7`.

The fix in this snapshot compiles `XposedHelpers.findAndHookMethod` with the Xposed API's actual return type, `XC_MethodHook.Unhook`. Compiling it as `Object` produces a runtime `NoSuchMethodError` and prevents the game hook from loading.

The files under `stubs/` are compile-time API stubs only. Do not package them into the APK; the running module must resolve these types from the installed Xposed framework. The Android 7 sources under `src/` are the generated package-specific sources used for the repaired APK. The local signing keystore and APK artifact are intentionally not included.
