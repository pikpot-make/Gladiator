package com.pitag.gladiatormod;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Companion status screen; the compatible Xposed framework loads the hook in the game process. */
public final class MainActivity extends Activity {
    private static final int BG = 0xff111015;
    private static final int SURFACE = 0xff1c1820;
    private static final int SURFACE_ALT = 0xff27212b;
    private static final int TEXT = 0xfff6eedf;
    private static final int MUTED = 0xffb9ad9a;
    private static final int GOLD = 0xffd8b66d;
    private static final int RED = 0xffaa554d;
    private static final int GREEN = 0xff83c995;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private TextView status;
    private TextView details;
    private Button injectButton;
    private boolean enabled;
    private final Runnable refresher = new Runnable() {
        @Override public void run() {
            refresh();
            handler.postDelayed(this, 1800L);
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        build();
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(refresher);
        handler.post(refresher);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(refresher);
        super.onPause();
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(20), dp(18), dp(20), dp(26));
        scroll.addView(page, new ScrollView.LayoutParams(-1, -2));

        LinearLayout brand = row();
        TextView monogram = text("GM", 18, BG, true);
        monogram.setGravity(Gravity.CENTER);
        monogram.setBackground(shape(GOLD, GOLD, 28));
        brand.addView(monogram, new LinearLayout.LayoutParams(dp(54), dp(54)));
        LinearLayout brandCopy = new LinearLayout(this);
        brandCopy.setOrientation(LinearLayout.VERTICAL);
        brandCopy.setPadding(dp(13), 0, 0, 0);
        brandCopy.addView(text("THE LUDUS", 11, GOLD, true));
        brandCopy.addView(text("GLADIATOR SAVE EDITOR", 16, TEXT, true));
        brandCopy.addView(text("OFFLINE  ·  SINGLE PLAYER", 11, MUTED, true));
        brand.addView(brandCopy, new LinearLayout.LayoutParams(0, -2, 1f));
        page.addView(brand);
        addSpace(page, 20);

        LinearLayout stateCard = card();
        LinearLayout stateHeading = row();
        stateHeading.addView(text("RUNTIME STATUS", 12, MUTED, true), new LinearLayout.LayoutParams(0, -2, 1f));
        status = text("CHECKING", 11, MUTED, true);
        status.setGravity(Gravity.CENTER);
        status.setPadding(dp(12), dp(7), dp(12), dp(7));
        status.setBackground(shape(SURFACE_ALT, 0xff493c4e, 18));
        stateHeading.addView(status);
        stateCard.addView(stateHeading);
        details = text("Looking for Gladiator Manager in this virtual device…", 14, TEXT, false);
        details.setPadding(0, dp(12), 0, 0);
        stateCard.addView(details);
        page.addView(stateCard);

        injectButton = actionButton("INJECT / ENABLE EDITOR", true);
        injectButton.setOnClickListener(v -> toggle());
        addSpaced(page, injectButton, 14);

        Button openGame = actionButton("Open Gladiator Manager", false);
        openGame.setOnClickListener(v -> launchGame());
        addSpaced(page, openGame, 8);

        Button check = actionButton("Refresh status", false);
        check.setOnClickListener(v -> refresh());
        addSpaced(page, check, 8);

        LinearLayout steps = card();
        steps.addView(text("QUICK START", 12, GOLD, true));
        addStep(steps, "01", "Enable the module in your Xposed manager, then reboot this virtual device.");
        addStep(steps, "02", "Open a local single-player save in slot 1–5.");
        addStep(steps, "03", "Tap the GM bubble inside the game to edit your ludus.");
        page.addView(steps);

        addSpace(page, 14);
        LinearLayout note = card();
        note.setBackground(shape(0xff211a1d, 0xff654449, 16));
        note.addView(text("ROOT ISN’T THE HOOK", 12, 0xffffc1a8, true));
        TextView noteCopy = text("The hook framework must be enabled inside this same virtual device. This editor only targets local saves; it does not replace the game app or edit achievements.", 13, MUTED, false);
        noteCopy.setPadding(0, dp(8), 0, 0);
        note.addView(noteCopy);
        page.addView(note);

        addSpace(page, 14);
        TextView footer = text("LOCAL SAVE  •  YOUR LUDUS ONLY  •  NO MULTIPLAYER", 10, MUTED, true);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(dp(6), dp(8), dp(6), dp(4));
        page.addView(footer);
        setContentView(scroll);
    }

    private void addStep(LinearLayout parent, String number, String copy) {
        LinearLayout step = row();
        TextView badge = text(number, 11, GOLD, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(shape(SURFACE_ALT, 0xff4a3e31, 18));
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(dp(34), dp(30));
        badgeParams.setMargins(0, dp(11), dp(10), 0);
        step.addView(badge, badgeParams);
        TextView description = text(copy, 13, TEXT, false);
        description.setPadding(0, dp(12), 0, 0);
        step.addView(description, new LinearLayout.LayoutParams(0, -2, 1f));
        parent.addView(step);
    }

    private void toggle() {
        Bundle state = ControlBridge.read(this);
        enabled = state.getBoolean("enabled", false);
        boolean fresh = System.currentTimeMillis() - state.getLong("updated", 0L) < 30000L;
        enabled = !enabled;
        ControlBridge.setEnabled(this, enabled);
        if (enabled && (!fresh || !state.getBoolean("hooked", false))) {
            Toast.makeText(this, "Editor enabled. If the hook is not loaded, enable the module and reboot this virtual device.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, enabled ? "Editor enabled. Return to your local save." : "Editor disabled.", Toast.LENGTH_SHORT).show();
        }
        refresh();
    }

    private void refresh() {
        Bundle state = ControlBridge.read(this);
        long updated = state.getLong("updated", 0L);
        boolean fresh = updated > 0L && System.currentTimeMillis() - updated < 30000L;
        boolean hooked = fresh && state.getBoolean("hooked", false);
        boolean running = fresh && state.getBoolean("running", false);
        boolean ready = fresh && state.getBoolean("save_ready", false);
        int slot = state.getInt("slot", 0);
        enabled = state.getBoolean("enabled", false);

        if (!fresh || !hooked) {
            setStatus("NOT DETECTED", 0xffffc0b8, 0xff462322, RED);
            details.setText("Open Gladiator Manager in this same virtual device. If it stays undetected, check that the Xposed module is enabled, then reboot the VM.");
        } else if (ready) {
            setStatus(enabled ? "EDITOR ACTIVE" : "SAVE READY", enabled ? 0xffc8f0d0 : 0xffffe0a0, enabled ? 0xff1e3827 : 0xff453719, enabled ? GREEN : GOLD);
            details.setText((running ? "Game is open" : "Game was detected recently") + "  ·  local slot " + slot + "  ·  " + (enabled ? "bubble enabled" : "tap Enable to show the bubble"));
        } else {
            setStatus(enabled ? "ARMED · WAITING" : "GAME DETECTED", 0xffffe0a0, 0xff453719, GOLD);
            details.setText("The game hook is loaded. Open a local save in slot 1–5; the GM bubble appears when that save is active.");
        }
        injectButton.setText(enabled ? "EJECT / DISABLE EDITOR" : "INJECT / ENABLE EDITOR");
    }

    private void setStatus(String text, int foreground, int background, int border) {
        status.setText(text);
        status.setTextColor(foreground);
        status.setBackground(shape(background, border, 18));
    }

    private void launchGame() {
        Intent intent = getPackageManager().getLaunchIntentForPackage("com.rene.gladiatormanager");
        if (intent == null) {
            Toast.makeText(this, "Install Gladiator Manager in this virtual device first.", Toast.LENGTH_LONG).show();
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    private LinearLayout card() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(15), dp(16), dp(15));
        layout.setBackground(shape(SURFACE, 0xff342c38, 16));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(8));
        layout.setLayoutParams(params);
        return layout;
    }

    private Button actionButton(String label, boolean primary) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(15);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(primary ? BG : TEXT);
        button.setMinHeight(dp(52));
        button.setPadding(dp(14), dp(10), dp(14), dp(10));
        button.setBackground(shape(primary ? GOLD : SURFACE, primary ? GOLD : 0xff4b404f, 14));
        button.setElevation(dp(2));
        return button;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (stroke != fill) drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private void addSpaced(LinearLayout parent, View child, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(bottom));
        parent.addView(child, params);
    }

    private void addSpace(LinearLayout parent, int height) {
        View space = new View(this);
        parent.addView(space, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
