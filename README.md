# WLZ Client v0.4

- Android 9+ (minSdk 28)
- arm64-v8a first
- Minecraft package target: com.mojang.minecraftpe
- UI module settings are persisted locally and mirrored into the native runtime bridge.
- Includes configurable Zoom/FPS/Fullbright/Lag parameters in the UI.

## Build

Requires Android SDK 35, JDK 17 and Gradle 8.7. GitHub Actions is included.

## Important

This repository does not bundle Minecraft's proprietary binary and does not bypass Microsoft/Xbox authentication or licensing.

The native bridge is a loadable runtime layer for the client project. It does **not** by itself inject into the separate Minecraft process; real in-process game hooks still have to be implemented/loaded in the Minecraft process for the toggles to change game internals.


## v0.4 UI
- Orange / graphite gray / moss green visual theme.
- Mobile-first dashboard with WLZ header, hero card, status card, action buttons and module cards.
- Existing module state bridge is preserved; this release is a UI/version update, not a claim of in-process Minecraft hooks.
- Pack Center entry is a UI placeholder until a runtime-safe pack import flow is implemented.
