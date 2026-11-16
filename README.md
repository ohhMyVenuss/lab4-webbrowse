# Lab 4 – LTM Explorer | HTTP Server + JavaFX Browser + Media Viewer

Ứng dụng thực hành Lập trình mạng gồm **HTTP/1.1 Server** (sử dụng JDK `com.sun.net.httpserver.HttpServer`) và **JavaFX Browser Client** (`WebView`, `HttpClient`, Apache PDFBox `3.0.8`, JavaFX `MediaPlayer`/`MediaView`). Hệ thống hỗ trợ duyệt web, kiểm tra HTTP `GET`/`HEAD`/`POST`, upload/download tệp, xem PDF nhiều trang và stream audio/video qua `HTTP Range` (`206 Partial Content`).

---

## Tài liệu chi tiết

- **[Kiến trúc hệ thống](docs/ARCHITECTURE.md)**: Sơ đồ kiến trúc Client–Server, bảng endpoint, cơ chế HTTP Range, bảo mật path traversal, luồng dữ liệu và cấu trúc mã nguồn.
- **[Hướng dẫn Set Up 2 Máy qua LAN](docs/SETUP-2-MACHINES.md)**: Hướng dẫn từng bước cài đặt, mở Firewall, cấu hình IP, kiểm tra kết nối và xử lý sự cố giữa Máy 1 (Server) và Máy 2 (Client).
- **[Biên bản kiểm thử cục bộ](docs/LOCAL-VERIFICATION.md)**: Trạng thái kiểm thử tài nguyên mẫu và các HTTP endpoint trên loopback.

---

## 1. Tính năng chính

- **HTTP Server (`vn.ltm.web.server.WebServerMain`)**:
  - Bind mặc định `0.0.0.0:8080` (cấu hình trong `config/server.properties`).
  - Phục vụ trang chủ HTML và tài nguyên tĩnh (`GET`, `HEAD`).
  - Hỗ trợ `HTTP Range` (`bytes=start-end` và suffix range) trả `206 Partial Content` + `Content-Range` cho stream MP3/MP4.
  - Hỗ trợ `GET /download/{filename}` kèm `Content-Disposition: attachment`.
  - API kiểm thử: `POST /api/login`, `POST /api/upload` (header `X-File-Name`, tối đa 50 MB), `GET /api/history`.
  - Không phụ thuộc thư viện ngoài — có thể chạy trực tiếp bằng JDK 21 (`javac`/`java`) hoặc Maven.
- **Browser Client (`vn.ltm.web.client.WebBrowserApp`)**:
  - **Web Browser**: JavaFX `WebView` với Back / Forward / Reload / Home, thanh URL, lịch sử truy cập.
  - **Network Inspector**: Gửi `GET`, `HEAD`, `POST /api/login`, `POST /api/upload` và xem Status, Headers, Body Preview.
  - **PDF Viewer**: Tải `/files/document.pdf` và render từng trang bằng Apache PDFBox 3.0.8.
  - **Audio / Video Player**: Stream `/files/music.mp3` và `/files/video.mp4` qua `MediaPlayer`/`MediaView` với thanh tiến trình, seek và Download.

---

## 2. Yêu cầu môi trường

- **Hệ điều hành**: Windows 10 / 11 trên cả 2 máy.
- **JDK**: JDK 21 trở lên (`java` và `javac`).
- **Maven**: Maven 3.9+ *(hoặc dùng Maven tích hợp trong IntelliJ IDEA)* cho Máy 2 (Client).
- **Mạng**: Hai máy kết nối cùng mạng LAN / Wi-Fi.

---

## 3. Chạy nhanh trên 2 máy bằng file `.bat`

### Máy 1 – HTTP Server
1. Nhấn chuột phải `scripts\open-firewall.bat` → **Run as administrator** để mở cổng TCP `8080`.
2. Chạy `scripts\run-server.bat` (script sẽ tự hiển thị IPv4 của Máy 1 và khởi động Server).
3. Ghi lại địa chỉ IPv4 LAN của Máy 1 (ví dụ `192.168.1.105`).

### Máy 2 – Browser Client
1. Chạy `scripts\setup-client.bat`, nhập IPv4 của Máy 1 để cập nhật `config\client.properties` và kiểm tra kết nối.
2. Chạy `scripts\run-client.bat` *(nếu chưa cài Maven trên PATH, mở project bằng IntelliJ IDEA và chạy `vn.ltm.web.client.WebBrowserApp` với Working Directory là thư mục gốc `Lab4-Web`)*.

> Xem chi tiết đầy đủ và cách xử lý lỗi tại **[docs/SETUP-2-MACHINES.md](docs/SETUP-2-MACHINES.md)**.

---

## 4. Danh sách Scripts

| Script | Máy sử dụng | Chức năng |
|---|---|---|
| `scripts\open-firewall.bat` | Máy 1 (Admin) | Mở cổng TCP 8080 trên Windows Firewall |
| `scripts\run-server.bat` | Máy 1 | Hiển thị IPv4 và khởi động `WebServerMain` (Maven hoặc fallback `javac`/`java`) |
| `scripts\setup-client.bat` | Máy 2 | Nhập IP Server vào `config\client.properties` và test kết nối |
| `scripts\run-client.bat` | Máy 2 | Khởi động JavaFX `WebBrowserApp` qua Maven |
| `scripts\check-connection.bat` | Máy 2 | Kiểm tra TCP 8080 và HTTP `HEAD` tới các endpoint chính |
| `scripts\build.bat` | Cả 2 / Dev | Build toàn bộ project (`mvn -U clean package`) |

---

## 5. API & Endpoints

| Method | Endpoint | Mục đích |
|---|---|---|
| `GET`, `HEAD` | `/` hoặc `/home` | Trang chủ `webroot/index.html` |
| `GET`, `HEAD` | `/files/{filename}` | Tài nguyên tĩnh + hỗ trợ `Range` (`206`) |
| `GET`, `HEAD` | `/download/{filename}` | Tải file từ `webroot/files/` (`attachment`) |
| `POST` | `/api/login` | Endpoint kiểm tra POST JSON demo |
| `POST` | `/api/upload` | Upload raw file qua header `X-File-Name` vào `data/uploads/` |
| `GET`, `HEAD` | `/api/history` | Trả tối đa 50 request gần nhất (JSON) |

---

## 6. Cấu trúc thư mục

```text
Lab4-Web/
├── config/
│   ├── client.properties
│   └── server.properties
├── docs/
│   ├── ARCHITECTURE.md
│   ├── LOCAL-VERIFICATION.md
│   └── SETUP-2-MACHINES.md
├── scripts/
│   ├── build.bat
│   ├── check-connection.bat
│   ├── check-web.ps1
│   ├── open-firewall.bat
│   ├── run-client.bat
│   ├── run-server.bat
│   └── setup-client.bat
├── src/main/
│   ├── java/vn/ltm/web/
│   │   ├── client/WebBrowserApp.java
│   │   └── server/WebServerMain.java
│   └── resources/styles/app.css
├── webroot/
│   ├── index.html
│   └── files/
│       ├── document.pdf
│       ├── music.mp3
│       ├── sample.txt
│       └── video.mp4
├── .gitignore
├── pom.xml
└── README.md
```
