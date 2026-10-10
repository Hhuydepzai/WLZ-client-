from pathlib import Path
import os
import shutil
import subprocess

APP = Path("app")
JAVA = APP / "src/main/java/com/wlz/client"
CPP = APP / "src/main/cpp"
RES = APP / "src/main/res/drawable"
VALUES = APP / "src/main/res/values"

def copy(name: str, dest: Path):
    src = Path("patch") / name
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dest)

def prepare_minecraft_application_stub():
    # Compile-only shim for the real superclass contained in Apollon V6.6.
    # This class is excluded from the helper APK by compileOnly; the final
    # combined APK resolves the superclass from the original base DEX.
    stub_root = APP / ".minecraft_application_stub"
    stub_dir = stub_root / "com/pairip/application"
    stub_dir.mkdir(parents=True, exist_ok=True)
    java_file = stub_dir / "Application.java"
    java_file.write_text(
        "package com.pairip.application;\n"
        "public class Application extends android.app.Application {}\n",
        encoding="utf-8",
    )

    sdk_home = Path(os.environ.get("ANDROID_HOME", ""))
    candidates = [sdk_home / "platforms" / "android-35" / "android.jar"]
    platforms = sdk_home / "platforms"
    if platforms.exists():
        candidates.extend(sorted(platforms.glob("android-*/android.jar")))
    sdk = next((p for p in reversed(candidates) if p.exists()), None)
    if sdk is None:
        raise SystemExit("Android SDK android.jar not found for compile stubs")

    classes = stub_root / "classes"
    classes.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        ["javac", "-source", "8", "-target", "8", "-cp", str(sdk),
         "-d", str(classes), str(java_file)],
        check=True,
    )
    jar = APP / "minecraft-application-stub.jar"
    jar.unlink(missing_ok=True)
    subprocess.run(["jar", "cf", str(jar), "-C", str(classes), "."], check=True)
    shutil.rmtree(stub_root, ignore_errors=True)

copy("build.gradle", APP / "build.gradle")
prepare_minecraft_application_stub()

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
]:
    copy(name, JAVA / name)

copy("wlzclient.cpp", CPP / "wlzclient.cpp")
copy("CMakeLists.txt", CPP / "CMakeLists.txt")
copy("AndroidManifest.xml", APP / "src/main/AndroidManifest.xml")
copy("styles.xml", VALUES / "styles.xml")
copy("wlz_icon.xml", RES / "wlz_icon.xml")
copy("wlz_splash.xml", RES / "wlz_splash.xml")

print("WLZ single-app patch applied")
