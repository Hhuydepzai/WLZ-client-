from pathlib import Path

app = Path("app")
main = app / "src/main/java/com/wlz/client/MainActivity.java"
manifest = app / "src/main/AndroidManifest.xml"
styles = app / "src/main/res/values/styles.xml"

s = main.read_text()
if "private void maybeStartOverlay()" not in s:
    marker = "    private void defineModules() {\n"
    method = """    private void maybeStartOverlay() {
        if (!Settings.canDrawOverlays(this)) return;
        try {
            Intent i = new Intent(this, OverlayService.class);
            if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i);
            else startService(i);
        } catch (Throwable ignored) {}
    }

"""
    s = s.replace(marker, method + marker, 1)
if "        maybeStartOverlay();\n" not in s:
    s = s.replace("        setContentView(buildUi());\n",
                  "        setContentView(buildUi());\n        maybeStartOverlay();\n", 1)

old = """    private void openOverlaySettings() {
        try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
        catch (Exception e) { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); }
    }
"""
new = """    private void openOverlaySettings() {
        if (Settings.canDrawOverlays(this)) {
            maybeStartOverlay();
            Toast.makeText(this, "WLZ Overlay đã bật", Toast.LENGTH_SHORT).show();
            return;
        }
        try { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); }
        catch (Exception e) { startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)); }
    }
"""
if old in s:
    s=s.replace(old,new,1)
s=s.replace(
    "    @Override protected void onResume() { super.onResume(); refreshStatus(); }\n",
    "    @Override protected void onResume() { super.onResume(); refreshStatus(); maybeStartOverlay(); }\n",
    1
)
main.write_text(s)

m = manifest.read_text()
if "android.permission.FOREGROUND_SERVICE_SPECIAL_USE" not in m:
    m=m.replace(
        '    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />\n',
        '    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />\n'
        '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />\n'
        '    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />\n',
        1
    )
if 'android:icon="@drawable/wlz_icon"' not in m:
    m=m.replace(
        '        android:label="WLZ Client"\n',
        '        android:label="WLZ Client"\n'
        '        android:icon="@drawable/wlz_icon"\n'
        '        android:roundIcon="@drawable/wlz_icon"\n',
        1
    )
if 'android:name=".OverlayService"' not in m:
    marker='        <activity\n            android:name=".MainActivity"\n'
    service='''        <service
            android:name=".OverlayService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="WLZ floating controls" />
        </service>
'''
    m=m.replace(marker,service+marker,1)
manifest.write_text(m)

t=styles.read_text()
if "@drawable/wlz_splash" not in t:
    t=t.replace(
        '        <item name="android:windowNoTitle">true</item>\n',
        '        <item name="android:windowNoTitle">true</item>\n'
        '        <item name="android:windowBackground">@drawable/wlz_splash</item>\n',
        1
    )
styles.write_text(t)
