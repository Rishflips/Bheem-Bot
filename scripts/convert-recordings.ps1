param(
    [Parameter(Mandatory)] [string] $InDir,
    [string] $OutDir,
    [string] $Ffmpeg
)
# Converts voice recordings (m4a, mp3, aac, ogg, opus, amr, 3gp, flac, wma or wav) to the format the speech study needs:
# 16 kHz, mono, 16-bit PCM WAV. The output file keeps the input's base name, so name each recording by its take ID
# (for example S02-cmd-029-n.m4a) before converting. Then run the checker:
#   .\gradlew.bat :eval:checkRecordings
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $OutDir) { $OutDir = Join-Path $root 'eval\recordings\audio' }
if (-not $Ffmpeg) {
    $found = Get-Command ffmpeg -ErrorAction SilentlyContinue
    if (-not $found) { throw 'ffmpeg was not found on PATH; pass -Ffmpeg <path to ffmpeg.exe>' }
    $Ffmpeg = $found.Source
}
if (-not (Test-Path $InDir)) { throw "input folder not found: $InDir" }
New-Item -ItemType Directory -Force $OutDir | Out-Null

$extensions = '.m4a', '.mp3', '.aac', '.ogg', '.opus', '.amr', '.3gp', '.flac', '.wma', '.wav'
$files = @(Get-ChildItem $InDir -File | Where-Object { $extensions -contains $_.Extension.ToLower() })
if ($files.Count -eq 0) { throw "no audio files found in $InDir" }

$converted = 0
$failed = @()
$oddNames = @()
foreach ($file in $files) {
    $target = Join-Path $OutDir ($file.BaseName + '.wav')
    & $Ffmpeg -y -loglevel error -i $file.FullName -ac 1 -ar 16000 -c:a pcm_s16le $target
    if ($LASTEXITCODE -eq 0 -and (Test-Path $target)) { $converted++ } else { $failed += $file.Name }
    if ($file.BaseName -notmatch '^S\d{2}-cmd-\d{3}-[qnf]$') { $oddNames += $file.Name }
}
Write-Host "converted $converted of $($files.Count) files into $OutDir"
if ($failed.Count -gt 0) { Write-Warning "ffmpeg failed on: $($failed -join ', ')" }
if ($oddNames.Count -gt 0) { Write-Warning "these names are not take IDs, so the study will not use them: $($oddNames -join ', ')" }
