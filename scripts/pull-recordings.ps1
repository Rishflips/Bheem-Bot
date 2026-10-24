param(
    [string] $Recordings
)
# Copies the recordings made in the phone's recorder to eval\recordings\audio on the computer (files already there are replaced).
# adb writes progress to stderr, which Windows PowerShell 5.1 turns into terminating errors under 'Stop'; check exit codes instead.
$ErrorActionPreference = 'Continue'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$root = Split-Path -Parent $PSScriptRoot
$base = '/sdcard/Android/data/app.bheem.bench/files'
if (-not $Recordings) { $Recordings = Join-Path $root 'eval\recordings' }

$attached = & $adb devices | Select-String -Pattern "`tdevice$"
if (-not $attached) { throw 'no authorised device attached (check USB debugging)' }
$onPhone = @((& $adb shell "ls $base/recordings/audio 2>/dev/null") | Where-Object { $_ -like '*.wav' })
if ($onPhone.Count -eq 0) { throw 'there are no recordings on the phone' }
New-Item -ItemType Directory -Force $Recordings | Out-Null
& $adb pull "$base/recordings/audio" $Recordings | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'adb pull failed' }
$here = @(Get-ChildItem (Join-Path $Recordings 'audio') -Filter '*.wav').Count
Write-Host "$($onPhone.Count) recordings on the phone, $here .wav files now in $(Join-Path $Recordings 'audio')"
Write-Host 'see what is missing or in the wrong format: .\gradlew.bat :eval:checkRecordings'
