<#
    One-time setup for MineBhop.

    The project ships without gradle/wrapper/gradle-wrapper.jar, because a binary blob is
    not something you want appearing in your source tree unexamined. This script fetches
    Gradle from the official distribution site, uses it to generate a real wrapper, and
    then builds the mod. After it has run once, use .\gradlew.bat for everything.

    Usage:   powershell -ExecutionPolicy Bypass -File .\bootstrap.ps1
#>

$ErrorActionPreference = 'Stop'

$gradleVersion = '9.5.1'
$requiredJava = 25

$root = $PSScriptRoot
$toolsDir = Join-Path $root '.gradle-dist'
$gradleHome = Join-Path $toolsDir "gradle-$gradleVersion"
$gradleBat = Join-Path $gradleHome 'bin\gradle.bat'

Write-Host '=== MineBhop bootstrap ===' -ForegroundColor Cyan

# --- Java check -------------------------------------------------------------
# Minecraft 26.2 targets Java 25, and the build compiles with --release 25.
$javaOk = $false

# Prefer JAVA_HOME, fall back to whatever is on PATH.
$javaExe = $null
if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $javaExe = Join-Path $env:JAVA_HOME 'bin\java.exe'
    Write-Host "Using JAVA_HOME: $env:JAVA_HOME"
} else {
    $found = Get-Command java -ErrorAction SilentlyContinue
    if ($found) { $javaExe = $found.Source }
}

if (-not $javaExe) {
    Write-Host 'No java found on PATH and JAVA_HOME is not set.' -ForegroundColor Yellow
} else {
    # Note: `java --version` prints to stdout, `java -version` prints to stderr. Redirecting a
    # native command's stderr in Windows PowerShell 5.1 wraps each line in an ErrorRecord, which
    # $ErrorActionPreference = 'Stop' then turns into a terminating error. So use --version.
    $firstLine = ((& $javaExe --version) | Out-String) -split "`r?`n" | Select-Object -First 1
    if ($firstLine -match '\s(\d+)(?:[.\s]|$)') {
        $major = [int]$Matches[1]
        Write-Host "Found Java $major  ($($firstLine.Trim()))"
        if ($major -ge $requiredJava) { $javaOk = $true }
    } else {
        Write-Host "Could not parse the Java version from: $firstLine" -ForegroundColor Yellow
    }
}

if (-not $javaOk) {
    Write-Host ''
    Write-Host "Java $requiredJava or newer is required (Minecraft 26.2 runs on Java 25)." -ForegroundColor Red
    Write-Host 'Install a JDK, then re-run this script:' -ForegroundColor Red
    Write-Host '    winget install EclipseAdoptium.Temurin.25.JDK'
    Write-Host '  or download it from https://adoptium.net/temurin/releases/?version=25'
    Write-Host ''
    Write-Host 'If you have a JDK 25 installed but not on PATH, point JAVA_HOME at it first.'
    exit 1
}

# --- Gradle -----------------------------------------------------------------
if (-not (Test-Path $gradleBat)) {
    $zipUrl = "https://services.gradle.org/distributions/gradle-$gradleVersion-bin.zip"
    $zipPath = Join-Path $toolsDir "gradle-$gradleVersion-bin.zip"

    New-Item -ItemType Directory -Force -Path $toolsDir | Out-Null
    Write-Host "Downloading Gradle $gradleVersion from services.gradle.org ..."
    Invoke-WebRequest -Uri $zipUrl -OutFile $zipPath

    Write-Host 'Extracting ...'
    Expand-Archive -Path $zipPath -DestinationPath $toolsDir -Force
    Remove-Item $zipPath
} else {
    Write-Host "Using Gradle already unpacked in $gradleHome"
}

# --- Wrapper ----------------------------------------------------------------
Write-Host 'Generating the Gradle wrapper ...'
& $gradleBat wrapper --gradle-version $gradleVersion --distribution-type bin
if ($LASTEXITCODE -ne 0) { throw "gradle wrapper failed with exit code $LASTEXITCODE" }

# --- Build ------------------------------------------------------------------
Write-Host 'Building the mod (first run downloads Minecraft and decompiles it - expect a few minutes) ...'
& (Join-Path $root 'gradlew.bat') build
if ($LASTEXITCODE -ne 0) { throw "build failed with exit code $LASTEXITCODE" }

Write-Host ''
Write-Host 'Done. The mod jar is in build\libs\' -ForegroundColor Green
Get-ChildItem (Join-Path $root 'build\libs\*.jar') | ForEach-Object { Write-Host "    $($_.Name)" }
Write-Host ''
Write-Host 'Copy the jar WITHOUT "-sources" in its name into your .minecraft\mods folder,'
Write-Host 'alongside the Fabric API jar.'
