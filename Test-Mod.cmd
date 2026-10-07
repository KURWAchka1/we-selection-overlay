@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\manual-test.ps1" %*
set "result=%ERRORLEVEL%"
echo.
if not "%result%"=="0" echo Test launcher failed. See the error above.
pause
exit /b %result%
