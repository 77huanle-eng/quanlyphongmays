@echo off
title Quan Ly Phong May - Server
cd /d "%~dp0"
echo ==============================================
echo   DANG KHOI DONG HE THONG MAY CHU (SERVER)...
echo ==============================================

if exist "C:\Program Files\OpenJDK\jdk-25\bin\javaw.exe" (
    start "" "C:\Program Files\OpenJDK\jdk-25\bin\javaw.exe" -jar "QuanLyPhongMay_Server\dist\QuanLyPhongMay_Server.jar"
    exit /b 0
)

start "" javaw -jar "QuanLyPhongMay_Server\dist\QuanLyPhongMay_Server.jar"
if %ERRORLEVEL% NEQ 0 (
    echo Khong tim thay javaw trong he thong, dang chay bang java...
    java -jar "QuanLyPhongMay_Server\dist\QuanLyPhongMay_Server.jar"
    pause
)
exit /b 0

