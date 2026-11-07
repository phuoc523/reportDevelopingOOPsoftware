@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\CHECK-ALL-1107.ps1" %*
echo.
echo ExitCode: %errorlevel%
pause
