from pathlib import Path
import shutil

app = Path("app")
main = app / "src/main/java/com/wlz/client/MainActivity.java"
overlay = app / "src/main/java/com/wlz/client/OverlayService.java"
native_cpp = app / "src/main/cpp/wlzclient.cpp"
manifest = app / "src/main/AndroidManifest.xml"
styles = app / "src/main/res/values/styles.xml"

def copy(src_name: str, dst: Path):
    src = Path("patch") / src_name
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(src, dst)

copy("MainActivity.java", main)
copy("OverlayService.java", overlay)
copy("wlzclient.cpp", native_cpp)
copy("AndroidManifest.xml", manifest)
copy("styles.xml", styles)

res = app / "src/main/res/drawable"
res.mkdir(parents=True, exist_ok=True)
copy("wlz_icon.xml", res / "wlz_icon.xml")
copy("wlz_splash.xml", res / "wlz_splash.xml")

print("WLZ v0.5 UI + overlay + launcher patch applied")
