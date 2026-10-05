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
copy("MainActivity.java", JAVA / "MainActivity.java")
copy("WlzModuleManager.java", JAVA / "WlzModuleManager.java")
copy("WlzRuntimeBridge.java", JAVA / "WlzRuntimeBridge.java")
copy("WlzPreloadService.java", JAVA / "WlzPreloadService.java")
copy("WlzOverlayController.java", JAVA / "WlzOverlayController.java")
copy("WlzKeyMapper.java", JAVA / "WlzKeyMapper.java")
copy("WlzControlEditorActivity.java", JAVA / "WlzControlEditorActivity.java")
copy("WlzHudActivity.java", JAVA / "WlzHudActivity.java")
copy("WlzInGameHud.java", JAVA / "WlzInGameHud.java")
copy("wlzclient.cpp", CPP / "wlzclient.cpp")
copy("CMakeLists.txt", CPP / "CMakeLists.txt")
copy("AndroidManifest.xml", APP / "src/main/AndroidManifest.xml")
copy("styles.xml", VALUES / "styles.xml")
copy("wlz_icon.xml", RES / "wlz_icon.xml")
copy("wlz_splash.xml", RES / "wlz_splash.xml")

print("WLZ 0.6 architecture patch applied")
