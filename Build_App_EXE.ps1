$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

Write-Host "=== DONG GOI UNG DUNG WINDOWS STANDALONE (.EXE) ===" -ForegroundColor Cyan

# 1. Chay Build_All truoc de dam bao JAR moi nhat
& "$root\Build_All.ps1"

$JPACKAGE = "C:\Program Files\OpenJDK\jdk-25\bin\jpackage.exe"
$JLINK    = "C:\Program Files\OpenJDK\jdk-25\bin\jlink.exe"
if (-not (Test-Path $JPACKAGE)) { $JPACKAGE = "jpackage" }
if (-not (Test-Path $JLINK))    { $JLINK    = "jlink" }

$tempJre = "$env:TEMP\lanpc_jre"
if (Test-Path $tempJre) { Remove-Item $tempJre -Recurse -Force }
Write-Host "`n--- 1. Tao runtime Java sieu nhe voi jlink ---" -ForegroundColor Yellow
& $JLINK --add-modules java.base,java.desktop,java.logging,java.management,jdk.management,java.prefs --strip-debug --no-header-files --no-man-pages --compress zip-6 --output $tempJre

Stop-Process -Name QuanLyPhongMay_Client, QuanLyPhongMay_Server -Force -ErrorAction SilentlyContinue
Start-Sleep -Milliseconds 500

$outApps = "$root\Apps_Dist"
if (Test-Path $outApps) {
    try {
        Remove-Item $outApps -Recurse -Force -ErrorAction SilentlyContinue
    } catch {}
}
New-Item -ItemType Directory -Path $outApps -Force | Out-Null

Write-Host "`n--- 2. Dong goi QuanLyPhongMay_Server.exe ---" -ForegroundColor Yellow
& $JPACKAGE --type app-image --name "QuanLyPhongMay_Server" --input "$root\QuanLyPhongMay_Server\dist" --main-jar "QuanLyPhongMay_Server.jar" --main-class "quanlyphongmay.server.ServerMain" --runtime-image $tempJre --dest $outApps

Write-Host "`n--- 3. Dong goi QuanLyPhongMay_Client.exe ---" -ForegroundColor Yellow
& $JPACKAGE --type app-image --name "QuanLyPhongMay_Client" --input "$root\QuanLyPhongMay_Client\dist" --main-jar "QuanLyPhongMay_Client.jar" --main-class "quanlyphongmay.client.ClientMain" --runtime-image $tempJre --dest $outApps

Write-Host "`n--- 4. Nen thanh cac file ZIP de phan phoi ---" -ForegroundColor Yellow
Compress-Archive -Path "$outApps\QuanLyPhongMay_Server" -DestinationPath "$root\QuanLyPhongMay_Server_App.zip" -Force
Compress-Archive -Path "$outApps\QuanLyPhongMay_Client" -DestinationPath "$root\QuanLyPhongMay_Client_App.zip" -Force

# Dong bo sang thu muc song song
$parallelDir = "C:\Users\ADMIN\Downloads\QuanLyPhongMay"
if (Test-Path $parallelDir) {
    Copy-Item "$root\QuanLyPhongMay_Server_App.zip" "$parallelDir\" -Force
    Copy-Item "$root\QuanLyPhongMay_Client_App.zip" "$parallelDir\" -Force
    if (-not (Test-Path "$parallelDir\Apps_EXE")) {
        Copy-Item $outApps "$parallelDir\" -Recurse -Force
    }
}

Remove-Item $tempJre -Recurse -Force -ErrorAction SilentlyContinue
Write-Host "`n========================================================" -ForegroundColor Green
Write-Host "DONG GOI THANH CONG! FILE EXE VA ZIP DA SAN SANG DE DUNG" -ForegroundColor Green
Write-Host "========================================================" -ForegroundColor Green

