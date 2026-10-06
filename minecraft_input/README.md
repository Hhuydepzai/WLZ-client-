# Minecraft runtime input

Thư mục này nhận cả **Minecraft universal APK ARM64** lẫn bộ split đã tách thành `filemc*.zip` + `assets*.zip`.

Yêu cầu cuối cùng sau khi ghép phải có:
- `lib/arm64-v8a/libminecraftpe.so`
- `AndroidManifest.xml`
- một hoặc nhiều `classes*.dex`
- toàn bộ `res/`, `resources.arsc` và `assets/` của đúng phiên bản Minecraft

CI tự ghép các ZIP split thành một APK-shaped runtime rồi mới decode/rebuild để chèn WLZ. Không cần chạy Minecraft bằng ứng dụng ngoài và không dùng overlay permission.

Runtime native WLZ dò capability trong `libminecraftpe.so`; signature/offsets phụ thuộc phiên bản Minecraft. Module nào không resolve được sẽ bị ẩn thay vì hiện nút giả.

Không commit Minecraft APK/runtime proprietary vào repository công khai.
