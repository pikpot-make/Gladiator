package com.pitag.gladiatormod;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.WeakHashMap;

/** In-process floating editor. It only receives the current single-player Player object. */
final class EditorOverlay {
    private static final String GAME = "com.rene.gladiatormanager.";
    private static final String[] TYPE_CLASSES = {
            GAME + "enums.WeaponType",
            GAME + "enums.EquipmentType",
            GAME + "world.armory.ItemType",
            GAME + "world.armory.MountType"
    };
    private static final String[] ITEM_CLASSES = {
            GAME + "world.armory.Weapon",
            GAME + "world.armory.Equipment",
            GAME + "world.armory.Item",
            GAME + "world.armory.Mount"
    };
    private static final String[] COLLECTIONS = {"getWeapons", "getEquipment", "getItems", "getMounts"};
    private static final String[] CATEGORIES = {"Weapons", "Armor, helmets & boots", "Accessories, boots & items", "Mounts"};
    private static final int MAX_STAT = 1_000_000;
    private static final int MAX_BALANCE = 1_000_000_000;
    private static final int EDITOR_BG = 0xff141116;
    private static final int EDITOR_SURFACE = 0xff211c25;
    private static final int EDITOR_TEXT = 0xfff4ebdb;
    private static final int EDITOR_MUTED = 0xffb8ac9a;
    private static final int EDITOR_GOLD = 0xffd8b66d;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final WeakHashMap<Activity, View> BUBBLES = new WeakHashMap<>();

    private EditorOverlay() { }

    static void onResumed(Activity activity) {
        if (!GAME.equals(activity.getPackageName() + ".")) return;
        if (!isSupportedActivity(activity)) {
            detach(activity);
            report(activity, false, 0);
            return;
        }
        Object app = activity.getApplication();
        int slot = intValue(invokeQuiet(app, "getSlot"), 0);
        Object player = invokeQuiet(app, "getPlayerState");
        boolean ready = slot >= 1 && slot <= 5 && player != null;
        boolean enabled = report(activity, ready, ready ? slot : 0);
        if (ready && enabled) attach(activity);
        else detach(activity);
    }

    static void onPaused(Activity activity) {
        detach(activity);
        Object app = activity.getApplication();
        int slot = intValue(invokeQuiet(app, "getSlot"), 0);
        Object player = invokeQuiet(app, "getPlayerState");
        boolean gameScreen = isSupportedActivity(activity);
        boolean ready = gameScreen && slot >= 1 && slot <= 5 && player != null;
        ControlBridge.report(activity, true, false, ready, ready ? slot : 0);
    }

    static void detach(Activity activity) {
        View bubble = BUBBLES.remove(activity);
        if (bubble != null) {
            if (bubble.getParent() instanceof ViewGroup) ((ViewGroup) bubble.getParent()).removeView(bubble);
        }
    }

    private static boolean report(Context context, boolean ready, int slot) {
        return ControlBridge.report(context, true, true, ready, slot);
    }

    private static boolean isSupportedActivity(Activity activity) {
        String name = activity.getClass().getName();
        return name.startsWith(GAME + "activities.")
                && !name.endsWith("SaveSlotActivity")
                && !name.endsWith("MainActivity")
                && !name.endsWith("MultiplayerGameActivity");
    }

    private static void attach(final Activity activity) {
        final View decorView = activity.getWindow().getDecorView();
        if (!(decorView instanceof ViewGroup)) return;
        View existing = BUBBLES.get(activity);
        if (existing instanceof View && ((View) existing).getParent() == decorView) return;
        if (existing instanceof View && ((View) existing).getParent() instanceof ViewGroup) {
            ((ViewGroup) ((View) existing).getParent()).removeView((View) existing);
        }
        final View bubble = new TextView(activity);
        TextView face = (TextView) bubble;
        face.setText("GM");
        face.setTextSize(14f);
        face.setTextColor(Color.WHITE);
        face.setGravity(Gravity.CENTER);
        face.setElevation(dp(activity, 9));
        face.setContentDescription("Open Gladiator save editor. Hold and drag to move.");
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(0xff7b271c);
        circle.setStroke(dp(activity, 2), 0xffffd36e);
        face.setBackground(circle);
        final int size = dp(activity, 54);
        final FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.LEFT);
        android.content.SharedPreferences prefs = activity.getApplicationContext().getSharedPreferences("gm_overlay_position", Context.MODE_PRIVATE);
        params.leftMargin = clamp(prefs.getInt("x", dp(activity, 18)), 0, Math.max(0, decorView.getWidth() - size));
        params.topMargin = clamp(prefs.getInt("y", dp(activity, 90)), 0, Math.max(0, decorView.getHeight() - size));
        BUBBLES.put(activity, bubble);
        if (decorView.getWidth() == 0 || decorView.getHeight() == 0) {
            decorView.post(() -> {
                if (BUBBLES.get(activity) == bubble && bubble.getParent() == null && activity.getWindow().getDecorView() == decorView) {
                    params.leftMargin = clamp(params.leftMargin, 0, Math.max(0, decorView.getWidth() - size));
                    params.topMargin = clamp(params.topMargin, 0, Math.max(0, decorView.getHeight() - size));
                    ((ViewGroup) decorView).addView(bubble, params);
                }
            });
        } else {
            ((ViewGroup) decorView).addView(bubble, params);
        }

        final int touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
        final int holdMs = ViewConfiguration.getLongPressTimeout();
        final int[] down = new int[4];
        final boolean[] dragging = {false};
        final boolean[] moved = {false};
        final Runnable[] longPress = new Runnable[1];
        bubble.setOnTouchListener((view, event) -> {
            if (!(view.getLayoutParams() instanceof FrameLayout.LayoutParams)) return false;
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    down[0] = (int) event.getRawX();
                    down[1] = (int) event.getRawY();
                    down[2] = lp.leftMargin;
                    down[3] = lp.topMargin;
                    dragging[0] = false;
                    moved[0] = false;
                    longPress[0] = () -> dragging[0] = true;
                    MAIN.postDelayed(longPress[0], holdMs);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int dx = (int) event.getRawX() - down[0];
                    int dy = (int) event.getRawY() - down[1];
                    if (!dragging[0] && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                        moved[0] = true;
                        if (longPress[0] != null) MAIN.removeCallbacks(longPress[0]);
                    }
                    if (dragging[0]) {
                        moved[0] = true;
                        lp.leftMargin = clamp(down[2] + dx, 0, Math.max(0, decorView.getWidth() - view.getWidth()));
                        lp.topMargin = clamp(down[3] + dy, 0, Math.max(0, decorView.getHeight() - view.getHeight()));
                        view.setLayoutParams(lp);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (longPress[0] != null) MAIN.removeCallbacks(longPress[0]);
                    if (dragging[0]) {
                        activity.getApplicationContext().getSharedPreferences("gm_overlay_position", Context.MODE_PRIVATE)
                                .edit().putInt("x", lp.leftMargin).putInt("y", lp.topMargin).apply();
                    } else if (!moved[0] && event.getActionMasked() == MotionEvent.ACTION_UP) {
                        view.performClick();
                    }
                    dragging[0] = false;
                    return true;
            }
            return true;
        });
        bubble.setOnClickListener(v -> openEditor(activity));
    }

    private static void openEditor(Activity activity) {
        Object app = activity.getApplication();
        int slot = intValue(invokeQuiet(app, "getSlot"), 0);
        Object player = invokeQuiet(app, "getPlayerState");
        if (slot < 1 || slot > 5 || player == null) {
            Toast.makeText(activity, "Open a single-player save first.", Toast.LENGTH_SHORT).show();
            return;
        }
        new EditorDialog(activity, app, player, slot).show();
    }

    private static final class EditorDialog {
        final Activity activity;
        final Object app;
        final Object player;
        final int slot;
        final ClassLoader gameLoader;
        final ArrayList<Object> roster = new ArrayList<>();
        final ArrayList<ItemChoice> catalog = new ArrayList<>();
        final java.util.HashMap<Integer, ArrayList<ItemChoice>> catalogCache = new java.util.HashMap<>();
        Spinner gladiatorSpinner;
        Spinner categorySpinner;
        EditText itemSearch;
        Button browseButton;
        TextView catalogStatus;
        TextView balances;
        TextView inventory;
        TextView traits;
        EditText[] statFields;
        EditText moneyField;
        EditText influenceField;
        ItemChoice selectedItem;
        AlertDialog dialog;
        TextView saveState;
        boolean dirty;

        EditorDialog(Activity activity, Object app, Object player, int slot) {
            this.activity = activity;
            this.app = app;
            this.player = player;
            this.slot = slot;
            this.gameLoader = app.getClass().getClassLoader();
        }

        void show() {
            ScrollView scroll = new ScrollView(activity);
            scroll.setFillViewport(true);
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(dp(activity, 18), dp(activity, 14), dp(activity, 18), dp(activity, 18));
            root.setBackground(rounded(EDITOR_BG, 0xff544331, 18, activity));
            scroll.addView(root, new ScrollView.LayoutParams(-1, -2));
            addLabel(root, "THE LUDUS  /  OFFLINE EDITION", 11, true).setTextColor(EDITOR_GOLD);
            addLabel(root, "Gladiator Save Editor", 23, true);
            addLabel(root, "LOCAL SLOT  ·  YOUR GLADIATORS ONLY  ·  NO MULTIPLAYER", 10, true).setTextColor(EDITOR_MUTED);
            saveState = addLabel(root, "ALL CHANGES SAVED", 11, true);
            saveState.setTextColor(0xff85cf96);

            addSection(root, "Denarii");
            balances = addLabel(root, "", 15, true);
            moneyField = numberField("Amount to add or set");
            LinearLayout moneyRow = row();
            moneyRow.addView(moneyField, weight());
            moneyRow.addView(button("Add", v -> changeMoney(false)), weight());
            moneyRow.addView(button("Set", v -> changeMoney(true)), weight());
            root.addView(moneyRow);

            addSection(root, "Influence");
            influenceField = numberField("Amount to add or set");
            LinearLayout influenceRow = row();
            influenceRow.addView(influenceField, weight());
            influenceRow.addView(button("Add", v -> changeInfluence(false)), weight());
            influenceRow.addView(button("Set", v -> changeInfluence(true)), weight());
            root.addView(influenceRow);

            addSection(root, "Your ludus gladiators");
            addLabel(root, "This list comes only from Player.GetGladiators() in the selected save. Opponent rosters are never read.", 13, false);
            gladiatorSpinner = new Spinner(activity);
            gladiatorSpinner.setBackground(rounded(EDITOR_SURFACE, 0xff4a404c, 10, activity));
            gladiatorSpinner.setPopupBackgroundDrawable(rounded(0xff302a34, 0xff514655, 8, activity));
            root.addView(gladiatorSpinner);
            String[] statLabels = {"Strength", "Cunning", "Initiative", "Health"};
            statFields = new EditText[statLabels.length];
            for (int i = 0; i < statLabels.length; i++) {
                LinearLayout r = row();
                r.addView(label(statLabels[i], 14, false), weight());
                statFields[i] = numberField("1 to " + MAX_STAT);
                r.addView(statFields[i], weight());
                root.addView(r);
            }
            root.addView(button("Apply stats and save", v -> applyStats()));
            LinearLayout traitRow = row();
            traitRow.addView(button("Add trait", v -> chooseTrait(true)), weight());
            traitRow.addView(button("Remove trait", v -> chooseTrait(false)), weight());
            root.addView(traitRow);
            traits = addLabel(root, "Traits", 13, false);

            addSection(root, "Complete item catalog");
            addLabel(root, "Includes enum items and the game's named special-item constructors, including secret equipment, sandals/boots, accessories, and mounts.", 13, false);
            categorySpinner = new Spinner(activity);
            categorySpinner.setAdapter(adapter(CATEGORIES));
            categorySpinner.setBackground(rounded(EDITOR_SURFACE, 0xff4a404c, 10, activity));
            categorySpinner.setPopupBackgroundDrawable(rounded(0xff302a34, 0xff514655, 8, activity));
            root.addView(categorySpinner);
            addLabel(root, "The catalog includes available quality variants and special factory items.", 12, false);
            LinearLayout searchRow = row();
            itemSearch = new EditText(activity);
            itemSearch.setHint("Search item names");
            itemSearch.setSingleLine(true);
            itemSearch.setTextColor(EDITOR_TEXT);
            itemSearch.setHintTextColor(EDITOR_MUTED);
            itemSearch.setPadding(dp(activity, 10), dp(activity, 5), dp(activity, 10), dp(activity, 5));
            itemSearch.setBackground(rounded(EDITOR_SURFACE, 0xff4a404c, 10, activity));
            searchRow.addView(itemSearch, weight());
            browseButton = button("Browse all items", v -> browseItems());
            searchRow.addView(browseButton, weight());
            root.addView(searchRow);
            catalogStatus = addLabel(root, "Choose a category, then browse the catalog.", 11, false);
            catalogStatus.setTextColor(EDITOR_MUTED);
            root.addView(button("Add selected catalog item", v -> addItem()));
            inventory = addLabel(root, "", 13, false);
            root.addView(button("Save changes", v -> persist()));
            root.addView(button("Save and close", v -> { if (persist() && dialog != null) dialog.dismiss(); }));

            dialog = new AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert)
                    .setView(scroll).create();
            dialog.setOnDismissListener(d -> { if (dirty) persist(); });
            dialog.setOnShowListener(d -> {
                Window window = dialog.getWindow();
                if (window != null) {
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    window.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                            (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.90f));
                }
            });
            dialog.show();
            Window shownWindow = dialog.getWindow();
            if (shownWindow != null) shownWindow.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.90f));
            loadRoster();
            categorySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    catalog.clear();
                    selectedItem = null;
                    itemSearch.setText("");
                    ArrayList<ItemChoice> cached = catalogCache.get(position);
                    if (cached != null) {
                        catalog.addAll(cached);
                        catalogStatus.setText(cached.size() + " catalog entries ready.");
                    } else {
                        catalogStatus.setText("Choose a category, then browse the catalog.");
                    }
                }
                @Override public void onNothingSelected(AdapterView<?> parent) { }
            });
            refreshBalances();
            refreshInventory();
        }

        private void loadRoster() {
            roster.clear();
            Object list = invokeQuiet(player, "GetGladiators");
            if (list instanceof List<?>) roster.addAll((List<?>) list);
            ArrayList<String> names = new ArrayList<>();
            for (Object item : roster) names.add(safeName(item));
            if (names.isEmpty()) names.add("No gladiators in this ludus");
            gladiatorSpinner.setAdapter(adapter(names.toArray(new String[0])));
            gladiatorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { showGladiator(); }
                @Override public void onNothingSelected(AdapterView<?> parent) { }
            });
            showGladiator();
        }

        private Object selectedGladiator() {
            if (roster.isEmpty() || gladiatorSpinner == null) return null;
            int p = gladiatorSpinner.getSelectedItemPosition();
            return p >= 0 && p < roster.size() ? roster.get(p) : null;
        }

        private void showGladiator() {
            Object gladiator = selectedGladiator();
            if (gladiator == null) {
                for (EditText field : statFields) field.setText("");
                traits.setText("Traits: none");
                return;
            }
            Object attrs = invokeQuiet(gladiator, "getRawAttributes");
            String[] getters = {"getStrength", "getCunning", "getInitiative", "getMaxLife"};
            for (int i = 0; i < getters.length; i++) statFields[i].setText(String.valueOf(invokeQuiet(attrs, getters[i])));
            renderTraits(gladiator);
        }

        private void applyStats() {
            Object gladiator = selectedGladiator();
            if (gladiator == null) { toast("No gladiator is selected."); return; }
            Object attrs = invokeQuiet(gladiator, "getRawAttributes");
            String[] getters = {"getStrength", "getCunning", "getInitiative", "getMaxLife"};
            String[] adjusters = {"adjustStrength", "adjustCunning", "adjustInitiative", "adjustMaxLife"};
            int[] values = new int[4];
            for (int i = 0; i < values.length; i++) {
                values[i] = parse(statFields[i], -1);
                if (values[i] < 1 || values[i] > MAX_STAT) { toast("Stats must be from 1 to " + MAX_STAT + "."); return; }
            }
            try {
                String[] fields = {"_strength", "_cunning", "_initiative", "_maxLife"};
                markDirty();
                for (int i = 0; i < values.length; i++) setIntField(attrs, fields[i], values[i]);
                showGladiator();
            } catch (Exception e) { fail("Could not apply stats", e); }
        }

        private void changeMoney(boolean set) {
            int amount = parse(moneyField, -1);
            if (amount < 0 || amount > MAX_BALANCE) { toast("Enter an amount from 0 to " + MAX_BALANCE + "."); return; }
            try {
                if (set) invoke(player, "SetDenarii", amount);
                else {
                    long next = ((Number) invoke(player, "GetDenarii")).longValue() + amount;
                    if (next > MAX_BALANCE) { toast("That would exceed the balance limit."); return; }
                    invoke(player, "AddDenarii", amount);
                }
                markDirty(); refreshBalances();
            } catch (Exception e) { fail("Could not update denarii", e); }
        }

        private void changeInfluence(boolean set) {
            int amount = parse(influenceField, -1);
            if (amount < 0 || amount > MAX_BALANCE) { toast("Enter an amount from 0 to " + MAX_BALANCE + "."); return; }
            try {
                long next = set ? amount : ((Number) invoke(player, "GetInfluence")).longValue() + amount;
                if (next > MAX_BALANCE) { toast("That would exceed the balance limit."); return; }
                int allocated = intValue(invokeQuiet(player, "getAllocatedInfluence"), 0);
                if (next < allocated) { toast("Influence cannot be set below the amount already allocated."); return; }
                setIntField(player, "_influence", (int) next);
                markDirty(); refreshBalances();
            } catch (Exception e) { fail("Could not update influence", e); }
        }

        private void chooseTrait(boolean add) {
            Object gladiator = selectedGladiator();
            if (gladiator == null) { toast("No gladiator is selected."); return; }
            try {
                List<?> current = (List<?>) invoke(gladiator, "getTraits");
                ArrayList<Object> types = new ArrayList<>();
                ArrayList<String> names = new ArrayList<>();
                if (add) {
                    Class<?> type = gameClass("com.rene.gladiatormanager.enums.TraitType");
                    for (Object value : type.getEnumConstants()) {
                        String name = ((Enum<?>) value).name();
                        if (isSentinel(name)) continue;
                        boolean exists = false;
                        for (Object trait : current) {
                            Object t = invoke(invoke(trait, "getType"), "name");
                            if (name.equals(String.valueOf(t))) { exists = true; break; }
                        }
                        if (!exists) { types.add(value); names.add(name); }
                    }
                } else {
                    for (Object trait : current) {
                        Object type = invoke(trait, "getType");
                        types.add(type); names.add(String.valueOf(invoke(type, "name")));
                    }
                }
                if (types.isEmpty()) { toast(add ? "No unused traits are available." : "This gladiator has no traits."); return; }
                new AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert).setTitle(add ? "Add any game trait" : "Remove trait")
                        .setItems(names.toArray(new String[0]), (d, which) -> {
                            try {
                                boolean changed;
                                if (add) {
                                    Class<?> factoryClass = gameClass("com.rene.gladiatormanager.factories.TraitFactory");
                                    Object factory = factoryClass.getDeclaredConstructor().newInstance();
                                    Object trait = invoke(factory, "CreateTrait", types.get(which));
                                    changed = Boolean.TRUE.equals(invoke(gladiator, "addTrait", trait));
                                } else changed = Boolean.TRUE.equals(invoke(gladiator, "removeTrait", types.get(which)));
                                if (!changed) { toast("The game rejected that trait change."); return; }
                                markDirty(); renderTraits(gladiator);
                            } catch (Exception e) { fail("Could not update trait", e); }
                        }).setNegativeButton("Cancel", null).show();
            } catch (Exception e) { fail("Could not read the game's trait list", e); }
        }

        private void renderTraits(Object gladiator) {
            ArrayList<String> names = new ArrayList<>();
            Object list = invokeQuiet(gladiator, "getTraits");
            if (list instanceof List<?>) for (Object trait : (List<?>) list) {
                Object type = invokeQuiet(trait, "getType");
                names.add(String.valueOf(invokeQuiet(type, "name")));
            }
            traits.setText(names.isEmpty() ? "Traits: none" : "Traits: " + join(names));
        }

        private void browseItems() {
            int category = categorySpinner.getSelectedItemPosition();
            if (category < 0 || category >= ITEM_CLASSES.length) return;
            ArrayList<ItemChoice> cached = catalogCache.get(category);
            if (cached == null) {
                if (browseButton == null || !browseButton.isEnabled()) return;
                browseButton.setEnabled(false);
                browseButton.setText("Preparing catalog…");
                catalogStatus.setText("Loading item data in the background so the game stays responsive…");
                final int requestedCategory = category;
                Thread worker = new Thread(() -> {
                    ArrayList<ItemChoice> built = new ArrayList<>();
                    Throwable failure = null;
                    try { built = buildCatalog(requestedCategory); }
                    catch (Throwable error) { failure = error; }
                    final ArrayList<ItemChoice> result = built;
                    final Throwable problem = failure;
                    activity.runOnUiThread(() -> {
                        if (dialog == null || !dialog.isShowing()) return;
                        browseButton.setEnabled(true);
                        browseButton.setText("Browse all items");
                        if (problem != null) {
                            android.util.Log.e("GMOfflineEditor", "Could not build item catalog", problem);
                            catalogStatus.setText("The item catalog could not be loaded.");
                            toast("Could not load the game's item catalog.");
                            return;
                        }
                        catalogCache.put(requestedCategory, result);
                        if (categorySpinner.getSelectedItemPosition() == requestedCategory) {
                            catalog.clear();
                            catalog.addAll(result);
                            catalogStatus.setText(result.size() + " catalog entries ready.");
                            showCatalogResults(requestedCategory);
                        }
                    });
                }, "GM item catalog");
                worker.setDaemon(true);
                worker.start();
                return;
            }
            catalog.clear();
            catalog.addAll(cached);
            showCatalogResults(category);
        }

        private void showCatalogResults(int category) {
            if (catalog.isEmpty()) { toast("The game returned no items for this category."); return; }
            String filter = itemSearch.getText().toString().trim().toLowerCase(Locale.ROOT);
            ArrayList<ItemChoice> filtered = new ArrayList<>();
            ArrayList<String> labels = new ArrayList<>();
            for (ItemChoice choice : catalog) {
                if (filter.isEmpty() || choice.label.toLowerCase(Locale.ROOT).contains(filter)) {
                    filtered.add(choice); labels.add(choice.label);
                }
            }
            if (filtered.isEmpty()) { toast("No catalog item matches that search."); return; }
            new AlertDialog.Builder(activity, android.R.style.Theme_Material_Dialog_Alert).setTitle("Select from " + CATEGORIES[category] + " (" + filtered.size() + ")")
                    .setItems(labels.toArray(new String[0]), (d, which) -> {
                        selectedItem = filtered.get(which);
                        itemSearch.setText(selectedItem.label);
                        itemSearch.setSelection(itemSearch.length());
                    }).setNegativeButton("Close", null).show();
        }

        private ArrayList<ItemChoice> buildCatalog(int category) {
            ArrayList<ItemChoice> result = new ArrayList<>();
            Class<?> typeClass = gameClass(TYPE_CLASSES[category]);
            Class<?> qualityClass = gameClass("com.rene.gladiatormanager.enums.QualityType");
            Class<?> itemClass = gameClass(ITEM_CLASSES[category]);
            if (typeClass == null || qualityClass == null || itemClass == null) return result;
            Set<String> seen = new HashSet<>();
            Object[] qualities = qualityClass.getEnumConstants();
            try {
                Constructor<?> constructor = itemClass.getConstructor(typeClass, qualityClass);
                Object[] types = typeClass.getEnumConstants();
                if (types != null && qualities != null) for (Object type : types) {
                    String enumName = ((Enum<?>) type).name();
                    if (isSentinel(enumName) || "Fist".equalsIgnoreCase(enumName) || "OnFoot".equalsIgnoreCase(enumName)) continue;
                    for (Object quality : qualities) {
                        String q = ((Enum<?>) quality).name();
                        if (isSentinel(q)) continue;
                        try {
                            Object probe = constructor.newInstance(type, quality);
                            String actualName = displayName(probe, enumName, "");
                            if (!isSentinel(actualName)) addChoice(result, new ItemChoice(category, enumName, q, null, null, null, displayName(probe, enumName, q), false), seen);
                        } catch (Throwable ignored) { }
                    }
                }
            } catch (Throwable ignored) { }
            for (Method method : itemClass.getDeclaredMethods()) {
                if (!Modifier.isStatic(method.getModifiers()) || !itemClass.isAssignableFrom(method.getReturnType())) continue;
                if (!(method.getName().startsWith("Get") || method.getName().startsWith("get"))) continue;
                Class<?>[] args = method.getParameterTypes();
                try {
                    method.setAccessible(true);
                    if (args.length == 0) {
                        Object item = method.invoke(null);
                        addFactoryChoice(result, category, method, null, null, item, seen);
                    } else if (args.length == 1 && args[0] == boolean.class) {
                        for (boolean value : new boolean[]{false, true}) {
                            Object item = method.invoke(null, value);
                            addFactoryChoice(result, category, method, boolean.class, String.valueOf(value), item, seen);
                        }
                    } else if (args.length == 1 && args[0] == Boolean.class) {
                        for (boolean value : new boolean[]{false, true}) {
                            Object item = method.invoke(null, Boolean.valueOf(value));
                            addFactoryChoice(result, category, method, Boolean.class, String.valueOf(value), item, seen);
                        }
                    } else if (args.length == 1 && args[0] == qualityClass && qualities != null) {
                        for (Object quality : qualities) {
                            String q = ((Enum<?>) quality).name();
                            if (isSentinel(q)) continue;
                            Object item = method.invoke(null, quality);
                            addFactoryChoice(result, category, method, qualityClass, q, item, seen);
                        }
                    } else if (args.length == 1 && args[0].isEnum()) {
                        Object[] constants = args[0].getEnumConstants();
                        if (constants != null) for (Object arg : constants) {
                            String argName = ((Enum<?>) arg).name();
                            if (isSentinel(argName)) continue;
                            Object item = method.invoke(null, arg);
                            addFactoryChoice(result, category, method, args[0], argName, item, seen);
                        }
                    }
                } catch (Throwable ignored) { }
            }
            result.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
            return result;
        }

        private void addFactoryChoice(ArrayList<ItemChoice> result, int category, Method method, Class<?> argClass, String argName, Object probe, Set<String> seen) {
            if (probe == null) return;
            String actual = displayName(probe, method.getName(), "");
            if (isSentinel(actual)) return;
            String label = actual + (argName == null ? "" : "  [" + argName + "]") + "  • special";
            addChoice(result, new ItemChoice(category, null, null, method.getName(),
                    argClass == null ? null : argClass.getName(), argName, label, true), seen);
        }

        private void addChoice(ArrayList<ItemChoice> result, ItemChoice choice, Set<String> seen) {
            String key = choice.label.toLowerCase(Locale.ROOT);
            if (seen.add(key)) result.add(choice);
        }

        private void addItem() {
            if (selectedItem == null) { toast("Browse the catalog and select an item first."); return; }
            try {
                Object item = selectedItem.create(this);
                if (item == null) { toast("This item could not be constructed by the game."); return; }
                Object list = invoke(player, COLLECTIONS[selectedItem.category]);
                if (!(list instanceof List<?>)) throw new IllegalStateException("Game inventory is unavailable");
                @SuppressWarnings("unchecked") List<Object> mutable = (List<Object>) list;
                mutable.add(item);
                markDirty(); refreshInventory();
                toast("Added " + displayName(item, "item", "") + " to inventory.");
            } catch (Exception e) { fail("Could not add this item", e); }
        }

        private void refreshBalances() {
            try { balances.setText("Current: " + invoke(player, "GetDenarii") + " denarii  |  " + invoke(player, "GetInfluence") + " influence"); }
            catch (Exception e) { balances.setText("Could not read current balance."); }
        }

        private void refreshInventory() {
            try {
                inventory.setText("Current inventory: " + count("getWeapons") + " weapons, " + count("getEquipment") +
                        " armor/equipment, " + count("getItems") + " accessories/items, " + count("getMounts") + " mounts.");
            } catch (Exception e) { inventory.setText("Could not read inventory."); }
        }

        private int count(String method) throws Exception { return ((List<?>) invoke(player, method)).size(); }

        private void markDirty() {
            dirty = true;
            if (saveState != null) {
                saveState.setText("UNSAVED CHANGES  ·  tap Save changes");
                saveState.setTextColor(0xffe5bd68);
            }
        }

        private boolean persist() {
            if (!dirty) return true;
            try {
                int activeSlot = intValue(invokeQuiet(app, "getSlot"), 0);
                Object activePlayer = invokeQuiet(app, "getPlayerState");
                if (activeSlot != slot || activePlayer != player) {
                    throw new IllegalStateException("The active save changed; these edits were not written to another slot");
                }
                Object login = invoke(player, "getLoginId");
                invoke(app, "setState", login);
                dirty = false;
                if (saveState != null) {
                    saveState.setText("ALL CHANGES SAVED");
                    saveState.setTextColor(0xff85cf96);
                }
                if (!activity.isFinishing()) Toast.makeText(activity, "Saved to this local save.", Toast.LENGTH_SHORT).show();
                return true;
            } catch (Exception e) { fail("Game could not save these changes", e); return false; }
        }

        private Class<?> gameClass(String name) {
            try { return Class.forName(name, true, gameLoader); }
            catch (Throwable e) { return null; }
        }

        private String displayName(Object item, String fallback, String quality) {
            Object name = invokeQuiet(item, "getName");
            String value = name == null ? fallback : String.valueOf(name);
            if (quality != null && !quality.isEmpty() && !"Regular".equalsIgnoreCase(quality)) value += " (" + quality + ")";
            return value;
        }

        private void fail(String message, Exception error) {
            android.util.Log.e("GMOfflineEditor", message, error);
            toast(message + ". See module log for details.");
        }

        private void toast(String text) { Toast.makeText(activity, text, Toast.LENGTH_SHORT).show(); }

        private EditText numberField(String hint) {
            EditText field = new EditText(activity);
            field.setHint(hint);
            field.setInputType(InputType.TYPE_CLASS_NUMBER);
            field.setSingleLine(true);
            field.setTextColor(EDITOR_TEXT);
            field.setHintTextColor(EDITOR_MUTED);
            field.setPadding(dp(activity, 11), dp(activity, 7), dp(activity, 11), dp(activity, 7));
            field.setBackground(rounded(EDITOR_SURFACE, 0xff4a404c, 10, activity));
            return field;
        }

        private Button button(String text, View.OnClickListener listener) {
            Button button = new Button(activity);
            button.setText(text);
            button.setAllCaps(false);
            button.setTextSize(13);
            button.setTypeface(null, 1);
            boolean primary = "Save changes".equalsIgnoreCase(text) || "Save and close".equalsIgnoreCase(text) || "Add selected catalog item".equalsIgnoreCase(text);
            button.setTextColor(primary ? EDITOR_BG : EDITOR_TEXT);
            button.setMinHeight(dp(activity, 44));
            button.setPadding(dp(activity, 8), dp(activity, 6), dp(activity, 8), dp(activity, 6));
            button.setBackground(rounded(primary ? EDITOR_GOLD : 0xff302a34, primary ? EDITOR_GOLD : 0xff514655, 10, activity));
            button.setOnClickListener(listener);
            return button;
        }

        private LinearLayout row() {
            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); return row;
        }

        private LinearLayout.LayoutParams weight() {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            params.setMargins(dp(activity, 2), dp(activity, 1), dp(activity, 2), dp(activity, 1)); return params;
        }

        private TextView label(String text, int size, boolean bold) {
            TextView v = new TextView(activity); v.setText(text); v.setTextSize(size); v.setTextColor(EDITOR_TEXT);
            if (bold) v.setTypeface(null, 1); v.setPadding(0, dp(activity, 4), dp(activity, 6), dp(activity, 4)); return v;
        }

        private TextView addLabel(LinearLayout root, String text, int size, boolean bold) {
            TextView v = label(text, size, bold); root.addView(v); return v;
        }

        private void addSection(LinearLayout root, String title) {
            LinearLayout heading = new LinearLayout(activity);
            heading.setOrientation(LinearLayout.HORIZONTAL);
            heading.setGravity(Gravity.CENTER_VERTICAL);
            heading.setPadding(0, dp(activity, 13), 0, dp(activity, 5));
            TextView v = label(title.toUpperCase(Locale.ROOT), 14, true);
            v.setTextColor(EDITOR_GOLD);
            heading.addView(v, new LinearLayout.LayoutParams(-2, -2));
            View line = new View(activity);
            line.setBackgroundColor(0xff514331);
            LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(0, dp(activity, 1), 1f);
            lineParams.setMargins(dp(activity, 10), 0, 0, 0);
            heading.addView(line, lineParams);
            root.addView(heading);
        }

        private ArrayAdapter<String> adapter(String[] values) {
            ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, android.R.layout.simple_spinner_item, values) {
                @Override public View getView(int position, View convertView, ViewGroup parent) {
                    TextView view = (TextView) super.getView(position, convertView, parent);
                    view.setTextColor(EDITOR_TEXT);
                    view.setPadding(dp(activity, 10), dp(activity, 8), dp(activity, 10), dp(activity, 8));
                    return view;
                }
                @Override public View getDropDownView(int position, View convertView, ViewGroup parent) {
                    TextView view = (TextView) super.getDropDownView(position, convertView, parent);
                    view.setTextColor(EDITOR_TEXT);
                    view.setBackgroundColor(0xff302a34);
                    view.setPadding(dp(activity, 14), dp(activity, 10), dp(activity, 14), dp(activity, 10));
                    return view;
                }
            };
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            return adapter;
        }

        private int parse(EditText field, int fallback) {
            try { return Integer.parseInt(field.getText().toString().trim()); }
            catch (Exception e) { return fallback; }
        }

        private String safeName(Object value) {
            Object name = invokeQuiet(value, "GetName");
            return name == null ? value.getClass().getSimpleName() : String.valueOf(name);
        }

        private final class ItemChoice {
            final int category;
            final String enumType;
            final String quality;
            final String factory;
            final String argumentType;
            final String argumentName;
            final String label;
            final boolean special;
            ItemChoice(int category, String enumType, String quality, String factory, String argumentType,
                       String argumentName, String label, boolean special) {
                this.category=category; this.enumType=enumType; this.quality=quality; this.factory=factory;
                this.argumentType=argumentType; this.argumentName=argumentName; this.label=label; this.special=special;
            }
            Object create(EditorDialog parent) throws Exception {
                Class<?> itemClass = parent.gameClass(ITEM_CLASSES[category]);
                if (itemClass == null) return null;
                if (factory != null) {
                    for (Method m : itemClass.getDeclaredMethods()) if (m.getName().equals(factory) && Modifier.isStatic(m.getModifiers())) {
                        m.setAccessible(true);
                        if (m.getParameterTypes().length == 0 && argumentType == null) return m.invoke(null);
                        if (m.getParameterTypes().length != 1 || argumentType == null || !argumentType.equals(m.getParameterTypes()[0].getName())) continue;
                        Class<?> arg = m.getParameterTypes()[0];
                        if (arg == boolean.class || arg == Boolean.class) return m.invoke(null, Boolean.valueOf("true".equals(argumentName)));
                        if (arg.isEnum()) {
                            @SuppressWarnings({"unchecked", "rawtypes"}) Object e = Enum.valueOf((Class) arg, argumentName);
                            return m.invoke(null, e);
                        }
                    }
                    return null;
                }
                Class<?> type = parent.gameClass(TYPE_CLASSES[category]);
                Class<?> qualityType = parent.gameClass("com.rene.gladiatormanager.enums.QualityType");
                if (type == null || qualityType == null) return null;
                @SuppressWarnings({"unchecked", "rawtypes"}) Object itemType = Enum.valueOf((Class) type, enumType);
                @SuppressWarnings({"unchecked", "rawtypes"}) Object q = Enum.valueOf((Class) qualityType, quality);
                return itemClass.getConstructor(type, qualityType).newInstance(itemType, q);
            }
        }
    }

    private static boolean isSentinel(String name) {
        if (name == null) return true;
        String n = name.replace("_", "").replace(" ", "").toLowerCase(Locale.ROOT);
        return "none".equals(n) || "empty".equals(n) || "unarmored".equals(n)
                || "helmetless".equals(n) || "nohelmet".equals(n) || "onfoot".equals(n);
    }

    private static String join(List<String> names) {
        StringBuilder out = new StringBuilder();
        for (String name : names) { if (out.length() > 0) out.append(", "); out.append(name); }
        return out.toString();
    }

    private static int intValue(Object value, int fallback) { return value instanceof Number ? ((Number) value).intValue() : fallback; }

    private static Object invokeQuiet(Object target, String method, Object... args) {
        try { return invoke(target, method, args); } catch (Throwable ignored) { return null; }
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        if (target == null) throw new IllegalStateException("Missing object for " + name);
        Class<?> type = target instanceof Class<?> ? (Class<?>) target : target.getClass();
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            for (Method method : c.getDeclaredMethods()) {
                if (!method.getName().equals(name) || method.getParameterTypes().length != args.length) continue;
                Class<?>[] params = method.getParameterTypes(); boolean matches = true;
                for (int i = 0; i < params.length; i++) if (args[i] != null && !boxed(params[i]).isAssignableFrom(args[i].getClass())) matches = false;
                if (!matches) continue;
                method.setAccessible(true); return method.invoke(target instanceof Class<?> ? null : target, args);
            }
        }
        throw new NoSuchMethodException(type.getName() + "." + name);
    }

    private static Class<?> boxed(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == int.class) return Integer.class; if (type == boolean.class) return Boolean.class;
        if (type == long.class) return Long.class; if (type == float.class) return Float.class;
        if (type == double.class) return Double.class; if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class; if (type == char.class) return Character.class; return type;
    }

    private static void setIntField(Object target, String fieldName, int value) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(fieldName); f.setAccessible(true); f.setInt(target, value); return; }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(fieldName);
    }

    private static int dp(Context context, int value) { return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f); }

    private static GradientDrawable rounded(int fill, int stroke, int radius, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(context, radius));
        if (stroke != fill) drawable.setStroke(dp(context, 1), stroke);
        return drawable;
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

}
