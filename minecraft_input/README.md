# Minecraft runtime input

Đặt **Minecraft universal APK ARM64** vào thư mục này để CI tạo bản WLZ Client tích hợp Minecraft.

Yêu cầu:
- có `lib/arm64-v8a/libminecraftpe.so`
- có `AndroidManifest.xml` và `classes.dex`
- có đầy đủ resources/assets của đúng phiên bản Minecraft

Khuyến nghị dùng một APK universal thay vì XAPK/APKS split. Runtime native WLZ sẽ tự dò signature trong `libminecraftpe.so`; signature/offsets là theo phiên bản Minecraft, nên đổi phiên bản có thể làm một số module bị ẩn cho tới khi có adapter tương ứng.

Không commit Minecraft APK được cấp phép cho người khác vào repository công khai.
