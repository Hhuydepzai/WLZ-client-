# WLZ Launcher 2.5.0

WLZ is a small Android launcher for an already-installed Minecraft Bedrock app. It does **not** package Minecraft, copy its DEX/native runtime, or replace the installed game.

## What works in this version

- Detects the known Bedrock package IDs `com.mojang.minecraftpe` and `com.mojang.minecraftpe.preview`.
- Opens the installed game through Android's launch intent.
- Shows whether a supported Minecraft package is installed and lets the user rescan.
- Uses a standalone WLZ application ID: `com.wlzclient.launcher`.
- Uses a dark/orange launcher UI and does not depend on Apollon's PairIP Application class.

## FPS, ping and CPS status

The launcher currently displays placeholders for FPS, ping and CPS. These values must be collected from code running inside the game:

- FPS needs a frame/render counter from the game process.
- Ping needs the current server connection or its network statistics.
- CPS needs Minecraft's in-game input/touch events.

A separate Android launcher cannot accurately read another app's private runtime state or global touch events. WLZ will not display fabricated values. A compatible, licensed in-game bridge/source is needed before these counters can be enabled.

## Build

GitHub Actions builds `WLZ-Launcher-2.5.0.apk` from the Android project in `app.zip` and `tools.zip`.

The build validates the APK ZIP, APK signature, application ID and launcher DEX. These checks confirm packaging, not real-device Minecraft compatibility; test on a device with Minecraft Bedrock installed.

## Migration

Older WLZ builds embedded or modified the Minecraft package. This launcher is a separate application (`com.wlzclient.launcher`) and launches the installed Bedrock app. The old embedded builds and this launcher are different architectures. Back up worlds before removing or replacing any Minecraft installation.
