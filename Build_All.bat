@echo off
title Bien dich Quan Ly Phong May
cd /d "%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Build_All.ps1"
if errorlevel 1 (
    echo.
    echo LOI: Co loi trong qua trinh bien dich!
    pause
    exit /b 1
)
echo.
pause
