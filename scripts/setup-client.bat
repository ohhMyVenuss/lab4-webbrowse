@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0.."

echo ==================================================
echo   LTM LAB 4 - CAU HINH CLIENT KET NOI MAY 1
echo ==================================================
if exist "config\client.properties" (
  echo [INFO] Cau hinh hien tai trong config\client.properties:
  type "config\client.properties"
  echo.
)

set "SERVER_IP="
set /p "SERVER_IP=Nhap IPv4 cua May 1 (Server) [vd: 192.168.1.100]: "
if "!SERVER_IP!"=="" set "SERVER_IP=192.168.1.100"

set "WEB_PORT="
set /p "WEB_PORT=Nhap Port HTTP Server [mac dinh 8080]: "
if "!WEB_PORT!"=="" set "WEB_PORT=8080"

(
  echo # Cau hinh Browser Client ^(May 2^)
  echo server.host=!SERVER_IP!
  echo web.port=!WEB_PORT!
) > "config\client.properties"

echo.
echo [OK] Da luu config\client.properties:
type "config\client.properties"
echo.

set "TEST_NOW="
set /p "TEST_NOW=Ban co muon kiem tra ket noi toi Server ngay bay gio khong? (y/n) [y]: "
if /i "!TEST_NOW!"=="" set "TEST_NOW=y"
if /i "!TEST_NOW!"=="y" (
  powershell -ExecutionPolicy Bypass -File "scripts\check-web.ps1" -Server "!SERVER_IP!" -Port !WEB_PORT!
)

pause
endlocal
