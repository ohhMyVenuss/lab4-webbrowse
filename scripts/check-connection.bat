@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0.."

set "SERVER_IP=%~1"
set "WEB_PORT=%~2"

if "!SERVER_IP!"=="" if exist "config\client.properties" (
  for /f "usebackq tokens=1,2 delims==" %%A in ("config\client.properties") do (
    if /i "%%A"=="server.host" set "SERVER_IP=%%B"
    if /i "%%A"=="web.port" set "WEB_PORT=%%B"
  )
)

if "!SERVER_IP!"=="" set "SERVER_IP=192.168.1.100"
if "!WEB_PORT!"=="" set "WEB_PORT=8080"

powershell -ExecutionPolicy Bypass -File "scripts\check-web.ps1" -Server "!SERVER_IP!" -Port !WEB_PORT!
pause
endlocal
