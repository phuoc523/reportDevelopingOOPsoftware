@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\PUBLISH-CHECK-1107.ps1" %*
echo.
echo ExitCode: %errorlevel%
pause
