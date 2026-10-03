from pathlib import Path

app = Path("app")
main = app / "src/main/java/com/wlz/client/MainActivity.java"
native_cpp = app / "src/main/cpp/wlzclient.cpp"
manifest = app / "src/main/AndroidManifest.xml"
styles = app / "src/main/res/values/styles.xml"

s = main.read_text()

if "private void maybeStartOverlay()" not in s:
    s = s.replace("    private void defineModules() {\n", """    private void maybeStartOverlay() {
        if (!Settings.canDrawOverlays(this)) return;
        try {
            Intent i = new Intent(this, OverlayService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Throwable ignored) {}
    }

    private void defineModules() {
""", 1)

if "        maybeStartOverlay();\n" not in s:
    s = s.replace("        setContentView(buildUi());\n", "        setContentView(buildUi());\n        maybeStartOverlay();\n", 1)

s = s.replace('        add("Super Fix Lag", "Giảm độ phức tạp render", 9);',
              '        add("FIX LAG", "3 cấp độ: Nhẹ / Mạnh / Siêu mạnh", 9);')

if "root.addView(fixLagCard()" not in s:
    s = s.replace("        root.addView(heroCard(), top(10));\n\n        status =",
                  "        root.addView(heroCard(), top(10));\n        root.addView(fixLagCard(), top(9));\n\n        status =", 1)

s = s.replace('''        if (module.index == 9) addSlider(row, "Mức giảm render", 1, 3, prefs.getInt("lag_profile", 1), v -> {
            prefs.edit().putInt("lag_profile", v).apply(); setNativeParam(3, v);
        });
''', '        if (module.index == 9) addFixProfileControls(row);\n', 1)

if "private void addFixProfileControls" not in s:
    marker = "    private void addSlider(LinearLayout parent, String label, int min, int max, int value, ValueSetter setter) {\n"
    methods = '''    private void addFixProfileControls(LinearLayout parent) {
        TextView info = text("FIX LAG: " + fixProfileName(prefs.getInt("lag_profile", 1)), 10, ORANGE_LIGHT, true);
        parent.addView(info, top(4));

        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        String[] names = {"1  NHẸ", "2  MẠNH", "3  SIÊU"};
        for (int i = 1; i <= 3; i++) {
            final int level = i;
            Button b = smallButton(names[i - 1]);
            b.setOnClickListener(v -> { applyFixProfile(level); recreate(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(42), 1);
            if (i > 1) p.leftMargin = dp(5);
            row.addView(b, p);
        }
        parent.addView(row, top(3));

        Switch effects = new Switch(this);
        effects.setText("Xóa hiệu ứng nặng");
        effects.setTextColor(TEXT);
        effects.setTextSize(11);
        effects.setChecked(prefs.getBoolean("fix_remove_effects", false));
        effects.setOnCheckedChangeListener((b,c) -> {
            prefs.edit().putBoolean("fix_remove_effects", c).apply();
            applyFixProfile(prefs.getInt("lag_profile", 1));
        });
        parent.addView(effects, lp(-1, -2));

        int level = prefs.getInt("lag_profile", 1);
        parent.addView(text("Texture: V1 = 4 màu  •  V2 = 4 màu  •  V3 = 3 màu", 10, MUTED, false), lp(-1, -2));
    }

    private View fixLagCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(11), dp(13), dp(11));
        card.setBackground(round(PANEL_2, MOSS, 1, 14));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.addView(text("FIX LAG", 14, TEXT, true), new LinearLayout.LayoutParams(0, -2, 1));
        TextView chip = text("3 CẤP", 9, ORANGE_LIGHT, true);
        chip.setPadding(dp(8), dp(5), dp(8), dp(5));
        chip.setGravity(Gravity.CENTER);
        chip.setBackground(round(MOSS_DARK, MOSS, 1, 12));
        head.addView(chip, lp(-2, -2));
        card.addView(head);

        int current = prefs.getInt("lag_profile", 1);
        card.addView(text(fixProfileName(current) + "  •  " + (current >= 3 ? "3 màu" : "4 màu") + " texture", 10, MUTED, false), top(3));

        LinearLayout levels = new LinearLayout(this);
        levels.setGravity(Gravity.CENTER_VERTICAL);
        String[] lv = {"1  NHẸ", "2  MẠNH", "3  SIÊU MẠNH"};
        for (int i = 1; i <= 3; i++) {
            final int level = i;
            Button b = actionButton(lv[i-1], level == current ? ORANGE : MOSS_DARK, TEXT);
            b.setOnClickListener(v -> { applyFixProfile(level); recreate(); });
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(44), 1);
            if (i > 1) p.leftMargin = dp(5);
            levels.addView(b, p);
        }
        card.addView(levels, top(7));

        Switch fx = new Switch(this);
        fx.setText("Xóa hiệu ứng nặng");
        fx.setTextColor(TEXT);
        fx.setTextSize(11);
        fx.setChecked(prefs.getBoolean("fix_remove_effects", current >= 2));
        fx.setOnCheckedChangeListener((b,c) -> {
            prefs.edit().putBoolean("fix_remove_effects", c).apply();
            applyFixProfile(prefs.getInt("lag_profile", 1));
        });
        card.addView(fx);

        card.addView(text("Cấp 1: fix nhẹ • 4 màu\nCấp 2: fix mạnh • 4 màu\nCấp 3: fix siêu mạnh • 3 màu",
                10, MUTED, false), top(2));
        return card;
    }

    private String fixProfileName(int level) {
        if (level == 1) return "CẤP 1 • FIX NHẸ";
        if (level == 2) return "CẤP 2 • FIX MẠNH";
        return "CẤP 3 • FIX SIÊU MẠNH";
    }

    private void applyFixProfile(int level) {
        level = Math.max(1, Math.min(3, level));
        boolean removeEffects = prefs.getBoolean("fix_remove_effects", level >= 2);
        int colors = level >= 3 ? 3 : 4;
        prefs.edit().putInt("lag_profile", level)
                .putBoolean("fix_remove_effects", removeEffects)
                .putInt("texture_colors", colors).apply();
        syncNative(9, true);
        setNativeParam(3, level);
        setNativeParam(4, removeEffects ? 1 : 0);
        setNativeParam(5, colors);
    }

'''
    s = s.replace(marker, methods + marker, 1)

s = s.replace(
'''    private void openOverlaySettings() {
        try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
        catch (Exception e) { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); }
    }
''',
'''    private void openOverlaySettings() {
        if (Settings.canDrawOverlays(this)) {
            maybeStartOverlay();
            Toast.makeText(this, "WLZ Overlay đã bật", Toast.LENGTH_SHORT).show();
            return;
        }
        try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
        catch (Exception e) { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); }
    }
''', 1)

s = s.replace("    @Override protected void onResume() { super.onResume(); refreshStatus(); }\n",
              "    @Override protected void onResume() { super.onResume(); refreshStatus(); maybeStartOverlay(); }\n", 1)
main.write_text(s)

cpp = native_cpp.read_text().replace("std::array<std::atomic<int>, 4> g_params{};",
                                      "std::array<std::atomic<int>, 6> g_params{};")
cpp = cpp.replace("    g_params[3].store(1); // lag profile",
                  "    g_params[3].store(1); // fix lag profile\n    g_params[4].store(0); // remove heavy effects\n    g_params[5].store(4); // texture colors")
native_cpp.write_text(cpp)

m = manifest.read_text()
if "android.permission.FOREGROUND_SERVICE_SPECIAL_USE" not in m:
    m = m.replace('    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />\n',
        '    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />\n'
        '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n'
        '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />\n', 1)
if 'android:icon="@drawable/wlz_icon"' not in m:
    m = m.replace('        android:label="WLZ Client"\n',
        '        android:label="WLZ Client"\n'
        '        android:icon="@drawable/wlz_icon"\n'
        '        android:roundIcon="@drawable/wlz_icon"\n', 1)
if 'android:name=".OverlayService"' not in m:
    m = m.replace('        <activity\n            android:name=".MainActivity"\n',
'''        <service
            android:name=".OverlayService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="WLZ floating controls" />
        </service>
        <activity
            android:name=".MainActivity"
''', 1)
manifest.write_text(m)

t = styles.read_text()
if "@drawable/wlz_splash" not in t:
    t = t.replace('        <item name="android:windowNoTitle">true</item>\n',
        '        <item name="android:windowNoTitle">true</item>\n'
        '        <item name="android:windowBackground">@drawable/wlz_splash</item>\n', 1)
styles.write_text(t)
