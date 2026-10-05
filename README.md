# WLZ Client v0.8.0

- Android 9+ (minSdk 28)
- arm64-v8a first
- Minecraft target package: `com.mojang.minecraftpe`
- Simple WLZ splash and orange/graphite control UI.
- Distinct WLZ monogram icon for launcher, splash and floating shortcut.
- WLZ preload service with persistent floating circular shortcut.
- WLZ module manager and native runtime bridge.
- Modules and Fix Lag profiles are persisted locally.
- The client code is an independent WLZ implementation. The supplied Flarial APK was used only as an architectural reference, not as a binary/code dependency.

## Runtime architecture

```
WLZ Launcher
    |
    v
WlzPreloadService
    |
    +--> WlzRuntimeBridge / libwlzruntime.so
    |
    +--> WlzOverlayController
              |
              +--> circular WLZ shortcut
              +--> ClickGUI / modules / Fix Lag
```

The floating shortcut is intentionally implemented as WLZ code and uses the WLZ icon.

## Important limitation

The repository does not bundle Minecraft's proprietary binary and does not include a copied Flarial native client. Because Android keeps Minecraft in a separate application process, this WLZ runtime does not by itself patch `libminecraftpe.so` or alter Minecraft's internal render/input/camera code.

The module manager and native runtime are therefore a clean integration layer for a future game-side hook/runtime. The UI state is real and persisted, but toggling a module is not claimed here to change Minecraft internals.

## Build

Requires Android SDK 35, JDK 17 and Gradle 8.7. GitHub Actions is included.


<!-- WLZ 0.8: embedded-Minecraft launch path + in-app HUD activity; no overlay permission. -->
