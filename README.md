# WLZ Client v0.9.0

- Android 9+ (minSdk 28)
- arm64-v8a
- WLZ is designed as a single-app Minecraft client: launcher, Minecraft Activity and WLZ HUD live in one APK.
- No overlay permission is used.
- Circular in-game WLZ logo opens ClickGUI.
- OTG keyboard mapping and draggable touch controls are persisted locally.
- Fix Lag profiles are stored locally.
- The supplied Minecraft runtime is expected under `minecraft_input/` during the build.
- Flarial was used only as an architectural reference. No Flarial binary or source is bundled.

## Single-app runtime

```
WLZ MainActivity
   |
   v
embedded com.mojang.minecraftpe.MainActivity
   |
   +--> WlzApplication lifecycle bridge
   +--> WlzInGameHud / WLZ logo / ClickGUI
   +--> WlzKeyMapper / touch controls
   +--> libwlzruntime.so
```

The embed step requires the ARM64 Minecraft native core:
`lib/arm64-v8a/libminecraftpe.so`.
Without that file the build intentionally stops instead of producing a fake "Minecraft" APK.

## Build

GitHub Actions builds a small WLZ helper first. When `minecraft_input/` contains the Minecraft APK or all required split parts, the workflow builds the single APK and signs the CI artifact.
