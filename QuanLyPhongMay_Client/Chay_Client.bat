@echo off
title Quan Ly Phong May - Client
cd /d "%~dp0"
echo Dang khoi dong Client Quan Ly Phong May...

if exist "C:\Program Files\OpenJDK\jdk-25\bin\javaw.exe" (
    start "" "C:\Program Files\OpenJDK\jdk-25\bin\javaw.exe" -jar dist\QuanLyPhongMay_Client.jar
    exit /b 0
)

start javaw -jar dist\QuanLyPhongMay_Client.jar
if %ERRORLEVEL% NEQ 0 (
    echo Khong tim thay javaw trong PATH, dang thu chay bang java console...
    java -jar dist\QuanLyPhongMay_Client.jar
    pause
)
