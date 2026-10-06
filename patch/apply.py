from pathlib import Path
import shutil

APP = Path("app")
JAVA = APP / "src/main/java/com/wlz/client"
CPP = APP / "src/main/cpp"
RES = APP / "src/main/res/drawable"
VALUES = APP / "src/main/res/values"

def copy(name: str, dest: Path):
    src = Path("patch") / name
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dest)

copy("build.gradle", APP / "build.gradle")
for name in [
    "MainActivity.java",
    "WlzApplication.java",
    "WlzLogoView.java",
    "WlzModuleManager.java",
    "WlzRuntimeBridge.java",
    "WlzKeyMapper.java",
    "WlzControlEditorActivity.java",
    "WlzInGameHud.java",
]:
    copy(name, JAVA / name)

copy("wlzclient.cpp", CPP / "wlzclient.cpp")
copy("CMakeLists.txt", CPP / "CMakeLists.txt")
copy("AndroidManifest.xml", APP / "src/main/AndroidManifest.xml")
copy("styles.xml", VALUES / "styles.xml")
copy("wlz_icon.xml", RES / "wlz_icon.xml")
copy("wlz_splash.xml", RES / "wlz_splash.xml")

print("WLZ single-app patch applied")
