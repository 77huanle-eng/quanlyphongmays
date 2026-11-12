@echo off
title Dong goi Ung dung Windows (.EXE)
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Build_App_EXE.ps1"
if errorlevel 1 (
    echo.
    echo LOI: Co loi trong qua trinh dong goi App!
    pause
    exit /b 1
)
echo.
pause

