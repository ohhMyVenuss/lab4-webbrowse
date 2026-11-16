@echo off
setlocal
net session >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Vui long chay file nay voi quyen Administrator ^(Chuot phai -^> Run as administrator^).
  pause
  exit /b 1
)

echo [INFO] Dang cap nhat Windows Firewall cho cong TCP 8080...
netsh advfirewall firewall delete rule name="LTM Web HTTP 8080" >nul 2>&1
netsh advfirewall firewall add rule name="LTM Web HTTP 8080" dir=in action=allow protocol=TCP localport=8080
if errorlevel 1 (
  echo [ERROR] Khong the them rule Firewall.
  pause
  exit /b 1
)

echo [OK] Da mo cong TCP 8080 thanh cong.
pause
endlocal
