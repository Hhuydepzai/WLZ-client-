# WLZ Client v2.4.0

- Android 8.0+ (API 26)
- ARM64 (arm64-v8a) devices only
- Keeps Android package ID `com.mojang.minecraftpe` for runtime compatibility; the visible app name and icon are WLZ Client.
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

The embed step preserves the source APK's assets and resource table, inserts the WLZ helper DEX/native library, and retains the original runtime package ID while applying the WLZ label/icon. It also patches the final binary manifest minimum SDK to API 26 (Android 8.0). CI validates the final ZIP, package ID, assets, resource table and ARM64 library before upload.

## Build status

The complete source APK must be attached to the runtime Release for the final build to succeed. Until it is present, CI intentionally exits with an explanatory error instead of publishing an APK known to be incomplete.


## Packaging/runtime fix

The packager now patches the original base APK's binary manifest instead of replacing it with the helper manifest. This preserves the source Minecraft permissions, providers, metadata, resource IDs, asset bundle and native bootstrap declarations, while setting the minimum supported SDK to API 26 (Android 8.0). WLZ keeps the source package ID `com.mojang.minecraftpe`; the visible app label and launcher icon are rebranded, and original runtime class names remain unchanged. The WLZ launcher is registered as the launcher entry and the original Minecraft activity keeps its remaining intent filters.

The Application extension is compiled against a compile-only stub for `com.pairip.application.Application`, the PairIP bootstrap observed in the supplied Apollon runtime DEX. The stub is not packaged in the final APK. Using a superclass not present in the base DEX can crash the app as soon as Android creates the Application object.

The merged APK intentionally retains the original package ID `com.mojang.minecraftpe` because changing it can break protected startup/runtime assumptions. It replaces an app installed under that package ID rather than living alongside it. Back up Minecraft worlds before uninstalling or replacing any existing installation.


## v2.4.0 UI/runtime changes

- Launcher and key-mapping screens use the WLZ dark/orange visual system and a clearer, vertically scrollable layout.
- The in-game WLZ ClickGUI opens automatically on first entry to Minecraft, with an opaque dark panel to replace the legacy purple panel visually.
- The overlay makes a single best-effort tap at the old panel's top-right close control before showing the WLZ GUI. This targets the legacy native overlay; it still requires device verification because the old panel is drawn by the embedded runtime, not fully represented in Android's View hierarchy.
- The WLZ floating control uses a dark background and orange crown/wordmark instead of a white circle.
- The build still preserves the full base assets/, resources.arsc, Minecraft native library, and original Minecraft bootstrap classes. No claim is made that every module works until it is tested on the target device.
