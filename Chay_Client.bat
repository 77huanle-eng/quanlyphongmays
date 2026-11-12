@echo off
title Quan Ly Phong May - Client
cd /d "%~dp0"
echo ==============================================
echo   DANG KHOI DONG MAY TRAM (CLIENT)...
echo ==============================================

if exist "C:\Program Files\OpenJDK\jdk-25\bin\javaw.exe" (
    start "" "C:\Program Files\OpenJDK\jdk-25\bin\javaw.exe" -jar "QuanLyPhongMay_Client\dist\QuanLyPhongMay_Client.jar"
    exit /b 0
)

start "" javaw -jar "QuanLyPhongMay_Client\dist\QuanLyPhongMay_Client.jar"
if %ERRORLEVEL% NEQ 0 (
    echo Khong tim thay javaw trong he thong, dang chay bang java...
    java -jar "QuanLyPhongMay_Client\dist\QuanLyPhongMay_Client.jar"
    pause
)
exit /b 0

