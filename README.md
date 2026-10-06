# WLZ Client v1.0.0

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

GitHub Actions builds a small WLZ helper first. When `minecraft_input/` contains the Minecraft APK or the supplied `filemc*.zip` + `assets*.zip` split set, the workflow builds the single APK and signs the CI artifact.


### Runtime tích hợp Minecraft

CI chỉ đóng gói APK Minecraft khi `minecraft_input/` có runtime Minecraft do người dùng cung cấp. Bản build hiện tại dùng một APK Minecraft ARM64 làm nguồn, giữ `com.mojang.minecraftpe.MainActivity`, thêm WLZ Application/HUD và native runtime vào cùng APK. Không cần overlay permission và không khởi chạy ứng dụng Minecraft ngoài.

File đầu vào tối thiểu cho đường build này là APK Minecraft có:
- `AndroidManifest.xml`
- `classes.dex`
- `lib/arm64-v8a/libminecraftpe.so`
- assets/resources đi kèm của chính APK

Đặt APK vào `minecraft_input/`. Với bộ split dạng `filemc*.zip` + `assets*.zip`, CI ghép toàn bộ file thành một APK-shaped runtime trước khi rebuild. Không cần cài Minecraft riêng.

Native WLZ 1.0.0 hiện có capability detection theo signature. Các module chỉ xuất hiện trong ClickGUI khi backend tương ứng thực sự resolve được: Zoom, Unlock FPS, Fullbright, Snaplook. FPS Counter là module HUD. Các module chưa có adapter đúng phiên bản sẽ bị ẩn thay vì hiển thị nút giả.

Nguồn tham khảo native: BedrockTools (MIT) cho kỹ thuật signature/patch; Flarial chỉ được dùng làm tham chiếu kiến trúc. Không đóng gói binary Flarial.
