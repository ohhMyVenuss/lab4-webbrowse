@echo off
setlocal
cd /d "%~dp0.."

where mvn >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Khong tim thay Maven ^(mvn^) tren PATH.
  echo Vui long cai Maven 3.9+ hoac build bang Maven Tool Window trong IntelliJ IDEA.
  pause
  exit /b 1
)

echo [INFO] Dang build project bang Maven...
call mvn -U clean package
if errorlevel 1 (
  echo [ERROR] Build that bai. Vui long kiem tra log Maven.
  pause
  exit /b 1
)

echo [OK] Build thanh cong. Ket qua nam trong thu muc target\
pause
endlocal
