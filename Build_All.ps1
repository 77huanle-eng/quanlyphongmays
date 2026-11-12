$ErrorActionPreference = "Stop"

$JAVAC = "C:\Program Files\OpenJDK\jdk-25\bin\javac.exe"
$JAR   = "C:\Program Files\OpenJDK\jdk-25\bin\jar.exe"
if (-not (Test-Path $JAVAC)) { $JAVAC = "javac" }
if (-not (Test-Path $JAR))   { $JAR   = "jar" }

$root = $PSScriptRoot

Write-Host "--- 1. Synchronizing source files across projects ---" -ForegroundColor Cyan
# Common -> Server and Client
Copy-Item "$root/QuanLyPhongMay/QuanLyPhongMay/src/quanlyphongmay/common/*" "$root/QuanLyPhongMay_Server/src/quanlyphongmay/common/" -Force -Recurse
Copy-Item "$root/QuanLyPhongMay/QuanLyPhongMay/src/quanlyphongmay/common/*" "$root/QuanLyPhongMay_Client/src/quanlyphongmay/common/" -Force -Recurse

# Server -> Server
Copy-Item "$root/QuanLyPhongMay/QuanLyPhongMay/src/quanlyphongmay/server/*" "$root/QuanLyPhongMay_Server/src/quanlyphongmay/server/" -Force -Recurse

# Client -> Client
Copy-Item "$root/QuanLyPhongMay/QuanLyPhongMay/src/quanlyphongmay/client/*" "$root/QuanLyPhongMay_Client/src/quanlyphongmay/client/" -Force -Recurse
Write-Host "  -> Synced common, server, and client packages."

function Build-Project($name, $srcDir, $mainClass, $outJar) {
    Write-Host "`n--- Building $name ---" -ForegroundColor Yellow
    $buildDir = "$root/$srcDir/../build/classes"
    if (Test-Path $buildDir) { Remove-Item $buildDir -Recurse -Force }
    New-Item -ItemType Directory -Path $buildDir -Force | Out-Null

    $javaFiles = @(Get-ChildItem -Path "$root/$srcDir" -Recurse -Filter *.java | ForEach-Object { $_.FullName })
    Write-Host "  Compiling $($javaFiles.Count) source files..."

    $sourcesFile = [System.IO.Path]::GetTempFileName()
    $encodedFiles = $javaFiles | ForEach-Object { '"' + ($_.Replace('\', '/')) + '"' }
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllLines($sourcesFile, $encodedFiles, $utf8NoBom)

    $proc = Start-Process -FilePath $JAVAC -ArgumentList @("-encoding", "UTF-8", "-d", "`"$buildDir`"", "@`"$sourcesFile`"") -NoNewWindow -Wait -PassThru
    Remove-Item $sourcesFile -Force

    if ($proc.ExitCode -ne 0) {
        Write-Host "ERROR: Compilation failed for $name (ExitCode $($proc.ExitCode))" -ForegroundColor Red
        exit 1
    }
    Write-Host "  Compilation successful!" -ForegroundColor Green

    $manifest = "$buildDir/MANIFEST.MF"
    "Manifest-Version: 1.0`nMain-Class: $mainClass`n" | Set-Content -Path $manifest -Encoding Ascii

    $outDir = [System.IO.Path]::GetDirectoryName("$root/$outJar")
    if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

    $jarProc = Start-Process -FilePath $JAR -ArgumentList @("cfm", "`"$root/$outJar`"", "`"$manifest`"", "-C", "`"$buildDir`"", ".") -NoNewWindow -Wait -PassThru
    if ($jarProc.ExitCode -ne 0) {
        Write-Host "ERROR: JAR packaging failed for $name" -ForegroundColor Red
        exit 1
    }
    $jarSize = (Get-Item "$root/$outJar").Length
    Write-Host "  Packaged JAR: $outJar ($('{0:N0}' -f $jarSize) bytes)" -ForegroundColor Green

    # Don dep thu muc build tam de giu thu muc gon sach
    Remove-Item (Split-Path $buildDir -Parent) -Recurse -Force -ErrorAction SilentlyContinue
}

# 1. Build Server standalone JAR
Build-Project "QuanLyPhongMay_Server" "QuanLyPhongMay_Server/src" "quanlyphongmay.server.ServerMain" "QuanLyPhongMay_Server/dist/QuanLyPhongMay_Server.jar"

# 2. Build Client standalone JAR
Build-Project "QuanLyPhongMay_Client" "QuanLyPhongMay_Client/src" "quanlyphongmay.client.ClientMain" "QuanLyPhongMay_Client/dist/QuanLyPhongMay_Client.jar"

# 3. Build NetBeans Unified JAR (quanlyphongmaytest.jar)
Build-Project "QuanLyPhongMay_NetBeans" "QuanLyPhongMay/QuanLyPhongMay/src" "quanlyphongmay.QuanLyPhongMay" "QuanLyPhongMay/QuanLyPhongMay/dist/quanlyphongmaytest.jar"
Copy-Item "$root/QuanLyPhongMay/QuanLyPhongMay/dist/quanlyphongmaytest.jar" "$root/QuanLyPhongMay/QuanLyPhongMay/dist/QuanLyPhongMay.jar" -Force

# 4. Dong bo sang thu muc QuanLyPhongMay song song (neu co) de nguoi dung chay o thu muc nao cung dung 100%
$parallelDir = "C:\Users\ADMIN\Downloads\QuanLyPhongMay"
if (Test-Path $parallelDir) {
    Write-Host "`n--- Dong bo sang thu muc song song: $parallelDir ---" -ForegroundColor Cyan
    $destServerDir = "$parallelDir\QuanLyPhongMay_Server\dist"
    $destClientDir = "$parallelDir\QuanLyPhongMay_Client\dist"
    $destNbDist1   = "$parallelDir\QuanLyPhongMay\QuanLyPhongMay\dist"
    $destNbDist2   = "$parallelDir\QuanLyPhongMay\dist"

    foreach ($d in @($destServerDir, $destClientDir, $destNbDist1, $destNbDist2)) {
        if (-not (Test-Path $d)) { New-Item -ItemType Directory -Path $d -Force | Out-Null }
    }

    Copy-Item "$root\QuanLyPhongMay_Server\dist\QuanLyPhongMay_Server.jar" "$destServerDir\" -Force
    Copy-Item "$root\QuanLyPhongMay_Client\dist\QuanLyPhongMay_Client.jar" "$destClientDir\" -Force
    Copy-Item "$root\QuanLyPhongMay\QuanLyPhongMay\dist\quanlyphongmaytest.jar" "$destNbDist1\" -Force
    Copy-Item "$root\QuanLyPhongMay\QuanLyPhongMay\dist\quanlyphongmaytest.jar" "$destNbDist2\" -Force
    Copy-Item "$root\QuanLyPhongMay\QuanLyPhongMay\dist\quanlyphongmaytest.jar" "$destNbDist1\QuanLyPhongMay.jar" -Force
    Copy-Item "$root\QuanLyPhongMay\QuanLyPhongMay\dist\quanlyphongmaytest.jar" "$destNbDist2\QuanLyPhongMay.jar" -Force

    Copy-Item "$root\Chay_Server.bat" "$parallelDir\" -Force
    Copy-Item "$root\Chay_Client.bat" "$parallelDir\" -Force
    Copy-Item "$root\Build_All.bat" "$parallelDir\" -Force
    Copy-Item "$root\Build_All.ps1" "$parallelDir\" -Force

    # Dong bo toan bo source code hien dai sang parallel workspace (xoa ma cu rac de NetBeans khong chay nham)
    $destSrcRoot1 = "$parallelDir\QuanLyPhongMay\QuanLyPhongMay\src"
    $destSrcRoot2 = "$parallelDir\QuanLyPhongMay\src"

    if (Test-Path "$destSrcRoot1\quanlyphongmay") {
        # Xoa cac file cu nam o root quanlyphongmay ngoai tru QuanLyPhongMay.java
        Get-ChildItem -Path "$destSrcRoot1\quanlyphongmay" -Filter "*.java" | Where-Object { $_.Name -ne "QuanLyPhongMay.java" } | Remove-Item -Force
    }
    if (Test-Path "$destSrcRoot2\quanlyphongmay") {
        Get-ChildItem -Path "$destSrcRoot2\quanlyphongmay" -Filter "*.java" | Where-Object { $_.Name -ne "QuanLyPhongMay.java" } | Remove-Item -Force
    }

    # Copy cay thu muc chuan src
    Copy-Item "$root\QuanLyPhongMay\QuanLyPhongMay\src\*" "$destSrcRoot1\" -Recurse -Force
    Copy-Item "$root\QuanLyPhongMay\QuanLyPhongMay\src\*" "$destSrcRoot2\" -Recurse -Force

    # Xoa bo cac package cu legacy neu co de khong gay xung dot NetBeans
    Remove-Item -Recurse -Force "$parallelDir\QuanLyPhongMay_Client\src\client" -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force "$parallelDir\QuanLyPhongMay_Client\src\common" -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force "$parallelDir\QuanLyPhongMay_Server\src\server" -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force "$parallelDir\QuanLyPhongMay_Server\src\common" -ErrorAction SilentlyContinue
    Remove-Item -Recurse -Force "$parallelDir\QuanLyPhongMay\kkk" -ErrorAction SilentlyContinue
    Remove-Item -Force "$parallelDir\QuanLyPhongMay\e2e_log.txt" -ErrorAction SilentlyContinue
    Remove-Item -Force "$parallelDir\QuanLyPhongMay\output.csv" -ErrorAction SilentlyContinue
    Remove-Item -Force "$parallelDir\QuanLyPhongMay\build-diag.xml" -ErrorAction SilentlyContinue

    # Dong bo subprojects Server & Client
    Copy-Item "$root\QuanLyPhongMay_Server\src\*" "$parallelDir\QuanLyPhongMay_Server\src\" -Recurse -Force
    Copy-Item "$root\QuanLyPhongMay_Client\src\*" "$parallelDir\QuanLyPhongMay_Client\src\" -Recurse -Force

    Write-Host "  -> Da dong bo toan bo file SOURCE, JAR va BAT sang thu muc song song thanh cong!" -ForegroundColor Green
}

# Don dep cac file zip du thua neu co de nguoi dung khong bi nham lan
Remove-Item "$root/QuanLyPhongMay_Server.zip" -Force -ErrorAction SilentlyContinue
Remove-Item "$root/QuanLyPhongMay_Client.zip" -Force -ErrorAction SilentlyContinue

Write-Host "`n==========================================" -ForegroundColor Green
Write-Host "ALL BUILDS & SYNCS COMPLETED SUCCESSFULLY!" -ForegroundColor Green
Write-Host "==========================================" -ForegroundColor Green
