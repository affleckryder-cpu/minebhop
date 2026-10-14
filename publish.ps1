<#
    Uploads the jars in dist\ for one mod version to CurseForge.

    Prints what it would upload and stops, unless -Publish is given. Uploads are public and cannot
    be undone from here, so look at the dry run first.

    The API token is read from the CURSEFORGE_TOKEN environment variable (set once with
    `setx CURSEFORGE_TOKEN "..."`); it is never written anywhere by this script.

    Usage:
        powershell -ExecutionPolicy Bypass -File .\publish.ps1                    # dry run, current version
        powershell -ExecutionPolicy Bypass -File .\publish.ps1 -Publish           # upload them all
        powershell -ExecutionPolicy Bypass -File .\publish.ps1 -Publish -Only '*fabric*1.21.11*'
#>
param(
    [string]$Version,                    # default: mod_version in gradle.properties
    [string]$Only = '*',                 # wildcard on the jar name
    [string]$ChangelogSection,           # heading in CHANGELOG.md, default: the version
    [ValidateSet('release', 'beta', 'alpha')][string]$ReleaseType = 'release',
    [switch]$Publish,
    [switch]$Again                       # upload jars that were uploaded before
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$projectId = 1661915
$api = 'https://minecraft.curseforge.com/api'

if (-not $Version) {
    $Version = (Select-String -Path (Join-Path $root 'gradle.properties') -Pattern '^mod_version=(.+)$').Matches[0].Groups[1].Value.Trim()
}

# setx writes to the user environment, which a shell opened earlier has not seen yet.
$token = $env:CURSEFORGE_TOKEN
if (-not $token) { $token = [Environment]::GetEnvironmentVariable('CURSEFORGE_TOKEN', 'User') }
if (-not $token) { throw 'CURSEFORGE_TOKEN is not set.' }

# The changelog is this version's section of CHANGELOG.md, without its heading.
if (-not $ChangelogSection) { $ChangelogSection = $Version }
$changelog = ''
$inSection = $false
foreach ($line in Get-Content (Join-Path $root 'CHANGELOG.md') -Encoding UTF8) {
    if ($line -match '^## ') {
        if ($inSection) { break }
        $inSection = ($line.Trim() -eq "## $ChangelogSection")
        continue
    }
    if ($inSection) { $changelog += "$line`n" }
}
# CHANGELOG.md is hard-wrapped at 100 columns. Markdown renders that as one paragraph, but the raw
# text is what shows in CurseForge's editor and in some launchers, so each paragraph and each
# bullet is joined back onto one line.
$unwrapped = @()
foreach ($line in ($changelog.Trim() -split "`n")) {
    $continues = $unwrapped.Count -gt 0 -and $unwrapped[-1].Trim() -and $line.Trim() -and $line -notmatch '^\s*([-*#]|\d+\.)\s'
    if ($continues) { $unwrapped[-1] += ' ' + $line.Trim() } else { $unwrapped += $line.TrimEnd() }
}
$changelog = $unwrapped -join "`n"
if (-not $changelog) { throw "CHANGELOG.md has no '## $ChangelogSection' section." }

# A jar built for one Minecraft version can declare that it runs on several.
$minecraftFor = @{ '26.1.2' = @('26.1', '26.1.1', '26.1.2') }

$headers = @{ 'X-Api-Token' = $token }
$types = Invoke-RestMethod "$api/game/version-types" -Headers $headers
$versions = Invoke-RestMethod "$api/game/versions" -Headers $headers

# CurseForge reuses names across unrelated lists ("1.21.1" is also a Bukkit version), so every
# lookup names the list it wants.
function Get-VersionId([string]$name, [string]$typeSlugPattern) {
    $typeIds = @($types | Where-Object { $_.slug -match $typeSlugPattern } | ForEach-Object { $_.id })
    $hit = @($versions | Where-Object { $_.name -eq $name -and $typeIds -contains $_.gameVersionTypeID })
    if ($hit.Count -ne 1) { throw "Expected one CurseForge version named '$name' in /$typeSlugPattern/, found $($hit.Count)." }
    $hit[0].id
}

$jars = @(Get-ChildItem (Join-Path $root 'dist') -Filter "minebhop-*-$Version+*.jar" |
    Where-Object { $_.Name -notlike '*-sources.jar' -and $_.Name -like $Only } | Sort-Object Name)
if (-not $jars) { throw "No jars for $Version matching '$Only' in dist\." }

# Every jar this script has uploaded, one name per line, so running it twice cannot post duplicates.
$ledger = Join-Path $root 'dist\published.txt'
$done = @(if (Test-Path $ledger) { Get-Content $ledger })
$skipped = @($jars | Where-Object { $done -contains $_.Name })
if ($skipped -and -not $Again) {
    $skipped | ForEach-Object { Write-Host "Skipping $($_.Name): already uploaded (see dist\published.txt; -Again to resend)" -ForegroundColor Yellow }
    $jars = @($jars | Where-Object { $done -notcontains $_.Name })
    if (-not $jars) { Write-Host 'Nothing left to upload.'; return }
}

$plan = foreach ($jar in $jars) {
    if ($jar.Name -notmatch '^minebhop-(fabric|neoforge|forge)-(.+)\+(.+)\.jar$') { throw "Unexpected jar name: $($jar.Name)" }
    $loader = $Matches[1]
    $mc = $Matches[3]
    # @(...) around the whole thing: an if-expression unwraps a one-element array to a bare string.
    $mcList = @(if ($minecraftFor.ContainsKey($mc)) { $minecraftFor[$mc] } else { $mc })
    $java = if ($mc -like '1.20*') { 'Java 17' } elseif ($mc -like '1.21*') { 'Java 21' } else { 'Java 25' }
    $loaderName = @{ fabric = 'Fabric'; neoforge = 'NeoForge'; forge = 'Forge' }[$loader]

    $ids = @($mcList | ForEach-Object { Get-VersionId $_ '^minecraft-' })
    $ids += Get-VersionId $loaderName '^modloader$'
    $ids += Get-VersionId $java '^java$'
    $ids += Get-VersionId 'Client' '^environment$'
    $ids += Get-VersionId 'Server' '^environment$'

    $metadata = [ordered]@{
        changelog     = $changelog
        changelogType = 'markdown'
        displayName   = "MineBhop $Version ($loaderName $($mcList[0])$(if ($mcList.Count -gt 1) { "-$($mcList[-1])" }))"
        gameVersions  = $ids
        releaseType   = $ReleaseType
    }
    if ($loader -eq 'fabric') {
        $metadata.relations = @{ projects = @(
            @{ slug = 'fabric-api'; type = 'requiredDependency' },
            @{ slug = 'modmenu'; type = 'optionalDependency' }) }
    }
    [pscustomobject]@{ Jar = $jar; Name = $metadata.displayName; Tags = "$($mcList -join ', '), $loaderName, $java, Client, Server"; Metadata = $metadata }
}

Write-Host "Project $projectId, version $Version, release type '$ReleaseType'" -ForegroundColor Cyan
$plan | ForEach-Object { Write-Host ("  {0,-40} {1}`n  {2,-40} [{3}]" -f $_.Jar.Name, $_.Name, '', $_.Tags) }
Write-Host "`nChangelog:`n$changelog`n"

if (-not $Publish) {
    Write-Host 'Dry run: nothing uploaded. Add -Publish to upload.' -ForegroundColor Yellow
    return
}

Add-Type -AssemblyName System.Net.Http
$client = New-Object System.Net.Http.HttpClient
$client.DefaultRequestHeaders.Add('X-Api-Token', $token)
foreach ($item in $plan) {
    $form = New-Object System.Net.Http.MultipartFormDataContent
    $form.Add((New-Object System.Net.Http.StringContent(($item.Metadata | ConvertTo-Json -Depth 6 -Compress), [Text.Encoding]::UTF8)), 'metadata')
    $file = New-Object System.Net.Http.ByteArrayContent(, [IO.File]::ReadAllBytes($item.Jar.FullName))
    $file.Headers.ContentType = 'application/java-archive'
    $form.Add($file, 'file', $item.Jar.Name)

    $response = $client.PostAsync("$api/projects/$projectId/upload-file", $form).Result
    $body = $response.Content.ReadAsStringAsync().Result
    if (-not $response.IsSuccessStatusCode) {
        # Stop at the first failure, so a mistake is not repeated across every jar.
        throw "Upload of $($item.Jar.Name) failed: $([int]$response.StatusCode) $body"
    }
    Add-Content -Path $ledger -Value $item.Jar.Name
    Write-Host "Uploaded $($item.Jar.Name) -> $body" -ForegroundColor Green
}
