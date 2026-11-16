@echo off
setlocal
cd /d "%~dp0.."

echo ==================================================
echo   LTM LAB 4 - KHOI DONG HTTP SERVER (MAY 1)
echo ==================================================
echo [INFO] Danh sach dia chi IPv4 cua may nay:
ipconfig | findstr /i "IPv4"
echo.
echo [INFO] Hay dung 1 trong cac IP LAN o tren de nhap vao May 2 ^(Client^).
echo ==================================================
echo.

where mvn >nul 2>nul
if not errorlevel 1 (
  echo [INFO] Khoi dong Server bang Maven...
  call mvn -q -DskipTests compile exec:java -Dexec.mainClass=vn.ltm.web.server.WebServerMain
  if errorlevel 1 pause
  exit /b %errorlevel%
)

echo [INFO] Khong tim thay Maven tren PATH. Chuyen sang bien dich truc tiep bang JDK...
where javac >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Khong tim thay javac. Vui long cai dat JDK 21 va them vao PATH.
  pause
  exit /b 1
)

if not exist "target\server-classes" mkdir "target\server-classes"
javac --release 21 -encoding UTF-8 -d "target\server-classes" "src\main\java\vn\ltm\web\server\WebServerMain.java"
if errorlevel 1 (
  echo [ERROR] Bien dich WebServerMain that bai.
  pause
  exit /b 1
)

echo [INFO] Dang chay WebServerMain...
java -cp "target\server-classes" vn.ltm.web.server.WebServerMain
if errorlevel 1 pause
endlocal
