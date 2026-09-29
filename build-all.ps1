<#
    Builds MineBhop for every supported Minecraft version on both loaders, and collects the jars
    into dist\.

    The shared movement code feeds both loaders and every Minecraft version, so after changing it
    this is the command to run -- it is how you avoid shipping a stale build for one combination.

    Usage:
        powershell -ExecutionPolicy Bypass -File .\build-all.ps1              # every version in versions\
        powershell -ExecutionPolicy Bypass -File .\build-all.ps1 26.3         # just one
#>
param([string[]]$Versions)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot

if (-not $env:JAVA_HOME) {
    $candidate = Join-Path $env:USERPROFILE '.jdks\jdk-25.0.4+7'
    if (Test-Path $candidate) { $env:JAVA_HOME = $candidate }
}
if ($env:JAVA_HOME) { $env:Path = "$env:JAVA_HOME\bin;$env:Path" }

if (-not $Versions) {
    $Versions = Get-ChildItem (Join-Path $root 'versions\*.properties') |
        ForEach-Object { $_.BaseName } | Sort-Object
}

$dist = Join-Path $root 'dist'
New-Item -ItemType Directory -Force -Path $dist | Out-Null

$loaders = @(
    @{ Name = 'fabric';   Dir = $root },
    @{ Name = 'neoforge'; Dir = (Join-Path $root 'neoforge') }
)

$results = @()
foreach ($mc in $Versions) {
    foreach ($loader in $loaders) {
        Write-Host ""
        Write-Host "=== $($loader.Name) / Minecraft $mc ===" -ForegroundColor Cyan
        Push-Location $loader.Dir
        try {
            # Gradle and javac write notes to stderr. Under 'Stop', Windows PowerShell turns those
            # into terminating errors whenever the caller redirects stderr, so judge by exit code.
            $ErrorActionPreference = 'Continue'
            & .\gradlew.bat build "-Pmc=$mc" --console=plain
            $ok = ($LASTEXITCODE -eq 0)
        } finally {
            $ErrorActionPreference = 'Stop'
            Pop-Location
        }

        if ($ok) {
            $libs = Join-Path $loader.Dir "build\$mc\libs"
            Get-ChildItem $libs -Filter '*.jar' | Where-Object { $_.Name -notlike '*-sources.jar' } |
                ForEach-Object {
                    Copy-Item $_.FullName -Destination $dist -Force
                    $results += [pscustomobject]@{ Loader = $loader.Name; Minecraft = $mc; Result = 'OK'; Jar = $_.Name }
                }
        } else {
            $results += [pscustomobject]@{ Loader = $loader.Name; Minecraft = $mc; Result = 'FAILED'; Jar = '' }
        }
    }
}

Write-Host ""
$results | Format-Table -AutoSize
if ($results.Result -contains 'FAILED') {
    throw 'One or more builds failed -- see output above.'
}
Write-Host "Jars collected in $dist" -ForegroundColor Green
