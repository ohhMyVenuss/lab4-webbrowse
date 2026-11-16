# Lab 4 – Local verification

Date: 2026-11-01

## Assets verified

- `webroot/files/document.pdf`: valid PDF 1.4, 2 pages, 50,535 bytes.
- `webroot/files/music.mp3`: MPEG Layer III, 44.1 kHz mono, ~9.04 seconds.
- `webroot/files/video.mp4`: H.264 video 1280×720 + AAC audio, 9 seconds, faststart metadata.

## Server tests performed on loopback

| Test | Result |
|---|---|
| GET `/` | HTTP 200, `text/html` |
| HEAD `/files/document.pdf` | HTTP 200, `application/pdf`, Content-Length present |
| GET `/files/document.pdf` | HTTP 200, full valid two-page PDF |
| Range GET `/files/music.mp3` bytes 0–99 | HTTP 206, exactly 100 bytes |
| Range GET `/files/video.mp4` bytes 0–499 | HTTP 206, exactly 500 bytes |
| HEAD `/download/document.pdf` | HTTP 200 + Content-Disposition attachment |
| POST `/api/login` demo JSON | HTTP 200 |
| POST `/api/upload` raw file | HTTP 201, bytes persisted in `data/uploads` |
| Missing static file | HTTP 404 |

Server code compiled with `javac --release 21`. `pom.xml` parsed as valid XML.

## Not yet verified

- Maven build of exact JavaFX + PDFBox dependencies (Maven is not installed in the packaging environment).
- JavaFX PDF page rendering and media playback on Windows.
- Network tests between two physical Windows PCs.

Run `mvn clean package` on Windows, then follow the README LAN test sequence.
