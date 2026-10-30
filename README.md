# LiveTV

Ứng dụng IPTV cho Android TV với giao diện 10-foot tối giản, lấy cảm hứng từ Google Live Channels. LiveTV tổng hợp kênh truyền hình Việt Nam và quốc tế từ nhiều nguồn M3U công khai, có EPG, tự chuyển nguồn khi stream lỗi và hỗ trợ điều khiển kênh bằng Google Assistant.

## Tính năng

- Xem trực tiếp kênh Việt Nam và quốc tế, giao diện tối ưu cho TV và điều khiển từ xa.
- Gộp kênh trùng từ nhiều nguồn, ưu tiên nguồn ổn định và chất lượng cao.
- Tự động failover sang nguồn khác khi stream lỗi, timeout hoặc bị chặn.
- Hỗ trợ HLS/DASH, 4K/FHD, header HTTP riêng cho từng stream.
- EPG XMLTV: chương trình hiện tại, chương trình tiếp theo và timeline 3 giờ.
- Guide nhiều tab: Việt Nam, Quốc tế, Chất lượng cao, Yêu thích, Tất cả.
- Điều hướng không cần nút Menu:
  - Lên/Xuống: đổi kênh trước/sau.
  - OK: mở guide hoặc chọn kênh.
  - Back: quay lại tab/đóng guide.
  - Trái/Phải trong guide: cuộn timeline.
- Tìm kênh bằng giọng nói qua Google Assistant:
  - “Mở VTV1”
  - “Mở kênh VTV3”
  - “Xem VTV3”
  - “Bật kênh VTV1”
- Tích hợp TV Input Framework (TIF), TV Search Provider và deep link để Assistant mở đúng kênh.
- Yêu thích kênh, chất lượng Auto/High/Saver, lưu kênh xem gần nhất.
- Cache catalog/EPG, khởi động nhanh và tối ưu cuộn guide trên thiết bị Android TV cấu hình thấp.

## Công nghệ

- Kotlin, Jetpack Compose, Material 3
- AndroidX Media3 / ExoPlayer
- OkHttp, kotlinx.serialization, Coil, WorkManager
- minSdk 26, targetSdk 36, compileSdk 37
- Build release với R8, APK khoảng 2,4 MB

## Cài đặt

Tải APK mới nhất tại [GitHub Releases](https://github.com/dungzual201/LiveTV/releases/latest), sau đó cài trực tiếp lên Android TV (cho phép cài ứng dụng ngoài Play Store) hoặc dùng ADB:

```bash
adb install -r LiveTV-1.4.1-release.apk
```

APK release đã được ký để sideload, không dùng để phát hành trên Google Play.

## Build từ mã nguồn

Yêu cầu:

- JDK 17
- Android SDK (compileSdk 37)
- Gradle wrapper có sẵn trong repo

Tạo `local.properties` trỏ tới Android SDK.

Windows:

```properties
sdk.dir=C\:\\Users\\<ten-ban>\\AppData\\Local\\Android\\Sdk
```

macOS/Linux:

```properties
sdk.dir=/Users/<ten-ban>/Library/Android/sdk
```

Build debug:

```bash
./gradlew assembleDebug
```

Build release:

```bash
./gradlew assembleRelease
```

APK đầu ra:

- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release.apk`

Chạy unit test:

```bash
./gradlew testDebugUnitTest
```

## Cấu trúc dự án

```text
app/src/main/java/com/livetv/vn/
├── data/       # Model, parser M3U/XMLTV, repository, cache, EPG
├── playback/   # ExoPlayer, chọn nguồn, failover, HTTP header
├── search/     # Search Provider cho Assistant và tìm kênh
├── sync/       # WorkManager đồng bộ nền
├── tif/        # TV Input Framework và đồng bộ kênh
├── ui/         # Compose UI, guide, settings, components
├── voice/      # Xử lý intent/deep link từ Assistant
├── AppGraph.kt
└── LiveTvApp.kt
```

## Ghi chú

- Nguồn IPTV trong app là các danh sách công khai của cộng đồng. LiveTV không cung cấp nội dung và không chịu trách nhiệm về bản quyền của các nguồn bên thứ ba.
- Một số kênh có thể không phát được do nguồn chết, giới hạn vùng hoặc thay đổi URL; app sẽ tự thử nguồn khác.
- Đã kiểm tra trên Xiaomi Android TV 11. Một số thiết bị có thể cần bật quyền micro/Assistant hoặc cài đặt ngôn ngữ phù hợp để nhận lệnh giọng nói.
- Repo này là mã nguồn riêng tư; vui lòng không chia sẻ APK hoặc mã nguồn ra ngoài khi chưa có sự đồng ý.
