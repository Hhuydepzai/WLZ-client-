from pathlib import Path
import shutil

APP = Path("app")
JAVA = APP / "src/main/java/com/wlz/client"
RES = APP / "src/main/res/drawable"
VALUES = APP / "src/main/res/values"

def copy(name: str, dest: Path):
    src = Path("patch") / name
    if not src.is_file():
        raise SystemExit("Required WLZ source is missing: " + str(src))
    dest.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dest)

copy("build.gradle", APP / "build.gradle")

# Standalone launcher: do not inherit the Minecraft/Apollon PairIP
# Application class, embed Minecraft's DEX, or create a compile-only stub.
for name in [
    "MainActivity.java",
    "WlzApplication.java",
]:
    copy(name, JAVA / name)

copy("AndroidManifest.xml", APP / "src/main/AndroidManifest.xml")
copy("styles.xml", VALUES / "styles.xml")
copy("wlz_icon.xml", RES / "wlz_icon.xml")

print("WLZ standalone launcher patch applied")
