# Hướng dẫn Set Up 2 Máy Kết Nối Thành Công (LAN / Wi-Fi)

Tài liệu này hướng dẫn từng bước để chạy **HTTP Server trên Máy 1** và **JavaFX Browser Client trên Máy 2** trong cùng mạng LAN hoặc Wi-Fi.

---

## 1. Yêu cầu trước khi bắt đầu

1. **Hệ điều hành**: Windows 10 hoặc Windows 11 trên cả 2 máy.
2. **Mạng**: Cả 2 máy kết nối **cùng một mạng Wi-Fi hoặc dây LAN** (cùng lớp mạng, ví dụ `192.168.1.x`).
   > [!WARNING]
   > Nếu đang dùng Wi-Fi công cộng hoặc Hotspot có bật *AP Isolation / Client Isolation*, hai máy sẽ không nhìn thấy nhau. Hãy dùng Wi-Fi gia đình/nội bộ hoặc phát Hotspot từ điện thoại/laptop không chặn giao tiếp nội bộ.
3. **Phần mềm**:
   - **Máy 1 (Server)**: Chỉ cần **JDK 21+** (có `java` và `javac`). Có Maven càng tốt, không có Maven vẫn chạy được bằng `scripts\run-server.bat`.
   - **Máy 2 (Client)**: Cần **JDK 21+** và **Maven 3.9+** (hoặc **IntelliJ IDEA** có Maven tích hợp) để tải thư viện JavaFX 21 & PDFBox 3.0.8.
4. **Mã nguồn**: Copy hoặc `git clone` thư mục `Lab4-Web` lên cả 2 máy.

---

## 2. Thiết lập Máy 1 (HTTP Server)

### Bước 1: Kiểm tra cấu hình Server
Mở file `config\server.properties`, đảm bảo giữ nguyên cấu hình bind mọi địa chỉ mạng:

```properties
web.bind=0.0.0.0
web.port=8080
web.root=webroot
web.uploads=data/uploads
```

### Bước 2: Mở cổng TCP 8080 trên Windows Firewall
1. Vào thư mục `scripts\`.
2. Nhấn **chuột phải** vào `open-firewall.bat` → chọn **Run as administrator**.
3. Màn hình hiện `[OK] Da mo cong TCP 8080 thanh cong.` là đạt.

> [!TIP]
> Nếu Windows hỏi mạng đang kết nối là **Public** hay **Private**, hãy chuyển Network Profile sang **Private** để Firewall không chặn kết nối LAN.

### Bước 3: Khởi động HTTP Server và lấy địa chỉ IPv4
1. Chạy `scripts\run-server.bat` (nháy đúp hoặc chạy trong CMD).
2. Script sẽ tự động in ra danh sách IPv4 của Máy 1, ví dụ:
   ```text
   IPv4 Address. . . . . . . . . . . : 192.168.1.105
   ```
   *(Lưu ý: Nếu máy có nhiều card mạng ảo như VMware/VirtualBox/WSL, hãy chọn IP của card **Wi-Fi** hoặc **Ethernet** thật).*
3. Khi Server sẵn sàng, console sẽ hiển thị:
   ```text
   [WEB] HTTP server ready at http://0.0.0.0:8080/ | root=...\webroot
   [WEB] Sample resources: /files/document.pdf, /files/music.mp3, /files/video.mp4
   ```
4. **Giữ cửa sổ CMD này luôn mở** trong suốt quá trình Máy 2 kết nối.

*(Nếu chạy bằng IntelliJ IDEA: chọn Main Class `vn.ltm.web.server.WebServerMain`, Working Directory là thư mục gốc `Lab4-Web`.)*

---

## 3. Thiết lập Máy 2 (Browser Client)

### Bước 1: Cấu hình IP của Máy 1
**Cách 1 (Nhanh — dùng file `.bat`):**
1. Chạy `scripts\setup-client.bat`.
2. Nhập địa chỉ IPv4 của Máy 1 (ví dụ `192.168.1.105`) và nhấn Enter.
3. Nhập Port `8080` (nhấn Enter để dùng mặc định).
4. Khi được hỏi kiểm tra kết nối ngay (`y/n`), nhấn `y` để test luôn.

**Cách 2 (Thủ công):**
Sửa file `config\client.properties`:
```properties
server.host=192.168.1.105
web.port=8080
```
> Không dùng `localhost` hoặc `127.0.0.1` khi chạy trên 2 máy khác nhau.

### Bước 2: Kiểm tra kết nối từ Máy 2 sang Máy 1
Chạy `scripts\check-connection.bat` (hoặc PowerShell):
```powershell
Test-NetConnection 192.168.1.105 -Port 8080
```
Kết quả mong đợi:
- `TcpTestSucceeded : True`
- Các endpoint `/`, `/files/document.pdf`, `/files/music.mp3`, `/files/video.mp4`, `/download/document.pdf` đều trả về `Status = 200`.

### Bước 3: Khởi động JavaFX Browser Client
- **Cách 1 (Command Line / `.bat`)**: Chạy `scripts\run-client.bat` *(yêu cầu đã cài Maven trên PATH)*.
- **Cách 2 (IntelliJ IDEA — khuyên dùng nếu máy chưa cài Maven ngoài)**:
  1. Mở thư mục `Lab4-Web` bằng IntelliJ IDEA.
  2. Chờ Maven tải xong dependencies (`javafx-controls`, `javafx-web`, `javafx-media`, `pdfbox`).
  3. Tạo/chạy Run Configuration với:
     - Main Class: `vn.ltm.web.client.WebBrowserApp`
     - Working Directory: thư mục gốc `Lab4-Web`

---

## 4. Kịch bản kiểm thử sau khi kết nối thành công

1. **Web Browser**: Ứng dụng tự mở `http://<IP-MAY-1>:8080/` và hiển thị trang **LTM Digital Campus**.
2. **HTTP Inspector**:
   - Bấm **GET · Lấy dữ liệu** → xem HTTP `200`, headers và HTML preview.
   - Bấm **HEAD · Kiểm tra header** → xem `Content-Type`, `Content-Length` (không tải body).
   - Bấm **POST · Gửi dữ liệu thử** → gửi JSON tới `/api/login`, nhận `200 OK`.
   - Bấm **POST · Upload tệp** → chọn 1 file trên Máy 2, kiểm tra file đã xuất hiện trong `data\uploads\` trên Máy 1.
3. **PDF Viewer**: Chuyển sang tab **PDF Viewer**, kiểm tra hiển thị `document.pdf` (2 trang) và nút chuyển trang trước/tiếp.
4. **Audio / Video Player**: Chuyển sang tab **Audio Player** (`music.mp3`) và **Video Player** (`video.mp4`), bấm **Phát** và thử kéo thanh tiến trình (kiểm tra HTTP Range `206`).
5. **Download**: Bấm **↓ Tải tệp** để lưu file về Máy 2.
6. **Kiểm tra Log trên Máy 1**: Console Server phải in ra từng request kèm **IP của Máy 2**, ví dụ:
   ```text
   [HTTP] GET / from /192.168.1.108:54321
   ```

---

## 5. Xử lý sự cố thường gặp (Troubleshooting)

| Hiện tượng | Nguyên nhân phổ biến | Cách khắc phục |
|---|---|---|
| `TcpTestSucceeded : False` / Timeout | Chưa mở Firewall hoặc đang ở Public Network | Chạy lại `scripts\open-firewall.bat` bằng quyền Admin; chuyển Wi-Fi trên Máy 1 sang **Private**. |
| Nhầm IP Máy 1 | Lấy nhầm IP của VMware / VirtualBox / WSL (`192.168.56.x`, `172.x`) | Chạy `ipconfig` trên Máy 1, tìm đúng mục **Wireless LAN adapter Wi-Fi** hoặc **Ethernet**. |
| Client vẫn kết nối `192.168.1.100` | Chưa sửa `config\client.properties` hoặc Working Directory sai | Chạy `scripts\setup-client.bat`; nếu chạy trong IntelliJ phải đặt Working Directory là thư mục `Lab4-Web`. |
| `mvn is not recognized` trên Máy 2 | Máy chưa cài Maven vào biến môi trường `PATH` | Cài Maven 3.9+ và thêm `bin` vào `PATH`, hoặc mở project bằng IntelliJ IDEA để chạy. |
| Video MP4 không có hình hoặc báo lỗi codec | Thiếu Windows Media Feature Pack / codec H.264 | Kiểm tra cập nhật Windows Media Player / codec trên Windows; thử phát `music.mp3` để tách biệt lỗi mạng và lỗi codec. |
