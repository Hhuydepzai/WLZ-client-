# WLZ Client v2.3.1

- Android 9+ (minSdk 28)
- ARM64 only
- Separate Android package ID: `com.wlz.client.launcher` (does not replace `com.mojang.minecraftpe`)
- Launcher, Minecraft Activity, WLZ HUD, circular ClickGUI and key mapper are packaged in one APK.
- The runtime build must preserve the source APK's `assets/` and `resources.arsc`.

## Important runtime requirement

The repository's old four-file runtime bundle (`AndroidManifest.xml`, `dex.zip`, `arm64-v8a.zip`, `libminecraftpe.so`) **is incomplete**: it contains DEX and native libraries but not Minecraft's `assets/` or `resources.arsc`. It can produce an APK that installs but crashes or shows a gray screen.

The build now refuses to publish that incomplete package. The GitHub Release used by the workflow must include a complete, matching source APK named `base.apk`, `Minecraft-base.apk`, `Minecraft.apk`, `minecraft-full.apk`, or `Apollon*.apk`. It must contain:

- `AndroidManifest.xml`
- `resources.arsc`
- `assets/`
- `classes*.dex`
- `lib/arm64-v8a/libminecraftpe.so`

The embed step preserves the source APK's assets and resource table, inserts the WLZ helper DEX/native library, and uses the helper's manifest with WLZ's unique package ID. CI validates the final ZIP, package ID, assets, resource table and ARM64 library before upload.

## Build status

The complete source APK must be attached to the runtime Release for the final build to succeed. Until it is present, CI intentionally exits with an explanatory error instead of publishing an APK known to be incomplete.
