# WLZ Client 2.4.3 — startup recovery build

- Android 8.0+ (API 26)
- ARM64 (`arm64-v8a`) devices only
- Preserves the protected runtime's original package ID `com.mojang.minecraftpe`. This replaces the existing Minecraft package and cannot be installed side-by-side. Back up worlds before installing.
- Includes the WLZ launcher, Minecraft activity, WLZ HUD/ClickGUI and key mapper.

## Packaging safeguards

The old four-file runtime bundle (`AndroidManifest.xml`, `dex.zip`, `arm64-v8a.zip` and `libminecraftpe.so`) is incomplete by itself: it lacks Minecraft `assets/` and `resources.arsc`, which can lead to a gray screen or crash. The build refuses to publish an incomplete package.

The packager preserves the complete base APK's:
- `assets/`
- `resources.arsc`
- original Minecraft/PairIP DEX and native bootstrap
- package ID, provider authorities and game storage identity

It replaces only the WLZ launcher/icon/branding and the WLZ helper DEX/runtime. When the original publisher download is unavailable, CI can rebase the freshly built helper onto the last complete verified WLZ APK. The rebasing code removes old standalone WLZ DEX files before inserting the new ones and updates the manifest idempotently, avoiding duplicate launcher activities/classes.

## Startup recovery

- Optional WLZ module initialization and lifecycle-hook registration are guarded so they cannot abort Application startup.
- The launcher has a safe fallback screen that logs exceptions under `WLZStartup` rather than leaving a blank/unresponsive window.
- The protected PairIP Application superclass is preserved; it must exist in the source DEX.
- The app still needs a real device test. CI verifies build, ZIP integrity, manifest identity, resource table, assets, native library and APK signature, but those checks do not prove Minecraft will launch on every device.

## Install note

Because the original package ID is retained for runtime compatibility, Android treats WLZ as the Minecraft application. Back up worlds and remove/replace the existing Minecraft installation only when ready to test. Do not assume worlds will be retained by the installer.
