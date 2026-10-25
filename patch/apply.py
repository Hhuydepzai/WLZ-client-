from pathlib import Path
import shutil

APP = Path("app")
JAVA = APP / "src/main/java/com/wlz/client"
CPP = APP / "src/main/cpp"
RES = APP / "src/main/res/drawable"
VALUES = APP / "src/main/res/values"


def copy(name: str, dest: Path):
    source = Path("patch") / name
    if not source.is_file():
        raise SystemExit("Required launcher source is missing: " + str(source))
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, dest)


copy("build.gradle", APP / "build.gradle")

# This is a standalone launcher now: it must not depend on Apollon's PairIP
# Application class or require an embedded Minecraft APK to compile.
for name in [
    "MainActivity.java",
    "WlzApplication.java",
    "WlzBootstrapProvider.java",
    "WlzLogoView.java",
    "WlzModuleManager.java",
    "WlzRuntimeBridge.java",
    "WlzKeyMapper.java",
    "WlzControlEditorActivity.java",
    "WlzInGameHud.java",
    "WlzLagPackManager.java",
]:
    copy(name, JAVA / name)

copy("wlzclient.cpp", CPP / "wlzclient.cpp")
copy("CMakeLists.txt", CPP / "CMakeLists.txt")
copy("AndroidManifest.xml", APP / "src/main/AndroidManifest.xml")
copy("styles.xml", VALUES / "styles.xml")
copy("wlz_icon.xml", RES / "wlz_icon.xml")
copy("wlz_splash.xml", RES / "wlz_splash.xml")

# Optional launcher logo and packaged fix-lag data. Missing assets must not
# break launcher startup; WlzLogoView draws a safe vector-like fallback.
source_assets = Path("patch/assets")
target_assets = APP / "src/main/assets"
for relative in [
    Path("wlz_logo.png"),
    Path("wlz_app_icon.png"),
    Path("wlz_packs/common_a.mcpack"),
    Path("wlz_packs/common_b.mcpack"),
    Path("wlz_packs/fixlag2.mcpack"),
    Path("wlz_packs/fixlag3.mcpack"),
]:
    source = source_assets / relative
    if source.is_file():
        target = target_assets / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source, target)

print("WLZ standalone launcher patch applied")
