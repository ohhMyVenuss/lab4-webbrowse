@echo off
setlocal
cd /d "%~dp0.."

echo ==================================================
echo   LTM LAB 4 - KHOI DONG BROWSER CLIENT (MAY 2)
echo ==================================================
if exist "config\client.properties" (
  echo [INFO] Cau hinh ket noi ^(config\client.properties^):
  type "config\client.properties"
  echo.
) else (
  echo [WARN] Khong tim thay config\client.properties. Hay chay scripts\setup-client.bat truoc.
)

where mvn >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Khong tim thay Maven ^(mvn^) tren PATH.
  echo Hay cai Maven 3.9+ hoac mo thu muc nay bang IntelliJ IDEA va chay class:
  echo   vn.ltm.web.client.WebBrowserApp
  pause
  exit /b 1
)

echo [INFO] Dang khoi dong JavaFX Browser Client...
call mvn -q javafx:run
if errorlevel 1 (
  echo [ERROR] Khoi dong Client that bai. Kiem tra log Maven o tren.
  pause
  exit /b 1
)
endlocal
