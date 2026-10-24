param(
    [Parameter(Mandatory)] [string] $Device,
    [Parameter(Mandatory)] [string] $ModelId,
    [Parameter(Mandatory)] [string] $ModelFile,
    [Parameter(Mandatory)] [string] $Recordings,
    [ValidateSet('cpu', 'gpu')] [string] $Backend = 'cpu',
    [string] $AudioForms = 'file',
    [ValidateSet('on', 'off')] [string] $AudioFirst = 'on',
    [ValidateSet('on', 'off')] [string] $Plan = 'on',
    [ValidateSet('gemma', 'moonshine')] [string] $Engine = 'gemma',
    [string] $MoonshineDir,
    [ValidateSet('off', 'names', 'decoys')] [string] $KeyTerms = 'off',
    [ValidateSet('on', 'off')] [string] $Live = 'off',
    [ValidateRange(10, 1000)] [int] $ChunkMs = 100,
    [int] $Limit = 0,
    [int] $TimeoutMinutes = 60
)
# Runs the speech study on the phone: pushes the recordings, starts the bench app in speech mode, pulls the result.
# $Recordings is a local folder with manifest.jsonl and an audio folder of <take ID>.wav files (see eval\recordings\README.md).
# adb writes progress to stderr, which Windows PowerShell 5.1 turns into terminating errors under 'Stop'; check exit codes instead.
$ErrorActionPreference = 'Continue'
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$root = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $root 'bench\build\outputs\apk\debug\bench-debug.apk'
$pkg = 'app.bheem.bench'
$base = "/sdcard/Android/data/$pkg/files"
# -Live on feeds each clip to Moonshine's streaming API in -ChunkMs pieces at the speed of speech (Moonshine only).
$name = "${Device}__${ModelId}__${Backend}__off__speech" + $(if ($Engine -eq 'moonshine') { '-moonshine' } else { '' }) + $(if ($Engine -eq 'moonshine' -and $Live -eq 'on') { '-live' } else { '' }) + $(if ($KeyTerms -ne 'off') { "-$KeyTerms" } else { '' })
$moonshineFolder = 'moonshine-small-streaming-en'
$moonshineFiles = 'adapter.ort', 'cross_kv.ort', 'decoder_kv.ort', 'encoder.ort', 'frontend.model.ort', 'frontend.weights.ort', 'streaming_config.json', 'tokenizer.bin'
if ($Engine -eq 'moonshine') {
    if (-not $MoonshineDir) { $MoonshineDir = Join-Path $root "models-local\$moonshineFolder" }
    $absent = @($moonshineFiles | Where-Object { -not (Test-Path (Join-Path $MoonshineDir $_)) })
    if ($absent.Count -gt 0) { throw "Moonshine files missing in ${MoonshineDir}: $($absent -join ', ')" }
}

if (-not (Test-Path $ModelFile)) { throw "model file not found: $ModelFile" }
if (-not (Test-Path $apk)) { throw "build the APK first: .\gradlew.bat :bench:assembleDebug" }
$manifest = Join-Path $Recordings 'manifest.jsonl'
$audioDir = Join-Path $Recordings 'audio'
if (-not (Test-Path $manifest)) { throw "manifest not found: $manifest" }
$wavCount = @(Get-ChildItem $audioDir -Filter '*.wav' -ErrorAction SilentlyContinue).Count
if ($wavCount -eq 0) { throw "no .wav files in $audioDir" }
$attached = & $adb devices | Select-String -Pattern "`tdevice$"
if (-not $attached) { throw 'no authorised device attached (check USB debugging)' }

& $adb install -r $apk | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'adb install failed' }
& $adb shell am force-stop $pkg
# The app must create models/ and results/ itself: a folder made through adb is owned by the shell user and the app cannot use it.
$modelsOwner = ((& $adb shell "stat -c %U $base/models 2>/dev/null") | Out-String).Trim()
if ($modelsOwner -eq 'shell') { & $adb shell rm -rf "$base/models" }
$recordingsOwner = ((& $adb shell "stat -c %U $base/recordings 2>/dev/null") | Out-String).Trim()
if ($recordingsOwner -eq 'shell') { & $adb shell rm -rf "$base/recordings" }
$moonshineOwner = ((& $adb shell "stat -c %U $base/models/$moonshineFolder 2>/dev/null") | Out-String).Trim()
if ($moonshineOwner -eq 'shell') { & $adb shell rm -rf "$base/models/$moonshineFolder" }
& $adb shell rm -rf "$base/results"
& $adb shell am start -n "$pkg/.BenchActivity" | Out-Null
Start-Sleep -Seconds 3
& $adb shell am force-stop $pkg

$remoteModel = "$base/models/$(Split-Path -Leaf $ModelFile)"
$localSize = (Get-Item $ModelFile).Length
$remoteSize = (& $adb shell "stat -c %s $remoteModel 2>/dev/null") -as [long]
if ($remoteSize -ne $localSize) {
    & $adb push $ModelFile $remoteModel
    if ($LASTEXITCODE -ne 0) { throw 'adb push failed (model)' }
}

if ($Engine -eq 'moonshine') {
    $remoteMoonshine = "$base/models/$moonshineFolder"
    $ownerNow = ((& $adb shell "stat -c %U $remoteMoonshine 2>/dev/null") | Out-String).Trim()
    if (-not $ownerNow -or $ownerNow -eq 'shell') { throw "the app did not create $moonshineFolder on the phone; reinstall the app and try again" }
    foreach ($file in $moonshineFiles) {
        $localFile = Join-Path $MoonshineDir $file
        $sizeOnPhone = (& $adb shell "stat -c %s $remoteMoonshine/$file 2>/dev/null") -as [long]
        if ($sizeOnPhone -ne (Get-Item $localFile).Length) {
            & $adb push $localFile "$remoteMoonshine/$file" | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "adb push failed ($file)" }
        }
    }
}

# Replace the recordings on the phone so a run never mixes two studies. The app made these folders (above), so it can read them.
$audioOwner = ((& $adb shell "stat -c %U $base/recordings/audio 2>/dev/null") | Out-String).Trim()
if (-not $audioOwner -or $audioOwner -eq 'shell') { throw 'the app did not create recordings/audio on the phone; reinstall the app and try again' }
& $adb shell "rm -f $base/recordings/manifest.jsonl; rm -rf $base/recordings/audio/*"
& $adb push $manifest "$base/recordings/manifest.jsonl" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'adb push failed (manifest)' }
& $adb push $audioDir "$base/recordings/" | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'adb push failed (audio)' }
$remoteWavs = ((& $adb shell "ls $base/recordings/audio | wc -l") | Out-String).Trim() -as [int]
if ($remoteWavs -ne $wavCount) { throw "expected $wavCount audio files on the phone but found $remoteWavs" }

& $adb shell rm -f "$base/results/$name.done"
& $adb shell am start -n "$pkg/.BenchActivity" `
    --es mode speech --es model_id $ModelId --es model_file (Split-Path -Leaf $ModelFile) --es device_label $Device `
    --es engine $Engine --es keyterms $KeyTerms --es live $Live --es chunk_ms "$ChunkMs" --es backend $Backend --es audio_forms $AudioForms --es audio_first $AudioFirst --es plan $Plan --ei limit $Limit | Out-Null

# Android freezes the app when it is not in front; bring it forward if something else took the screen.
$deadline = (Get-Date).AddMinutes($TimeoutMinutes)
while ((Get-Date) -lt $deadline) {
    Start-Sleep -Seconds 30
    if (& $adb shell "ls $base/results/$name.done 2>/dev/null") { break }
    if (-not (& $adb shell pidof $pkg)) { Write-Warning 'app process is gone; pulling partial results'; break }
    $top = (& $adb shell dumpsys activity activities | Select-String 'topResumedActivity' | Select-Object -First 1)
    if ($top -and ($top.ToString() -notmatch [regex]::Escape($pkg))) {
        Write-Warning 'the bench app is not in front; bringing it forward'
        & $adb shell monkey -p $pkg -c android.intent.category.LAUNCHER 1 | Out-Null
    }
}

$out = Join-Path $root 'eval\results'
New-Item -ItemType Directory -Force $out | Out-Null
$errorPath = Join-Path $out "$name.error"
Remove-Item $errorPath -ErrorAction SilentlyContinue
& $adb pull "$base/results/$name.error" $errorPath | Out-Null
if (Test-Path $errorPath) { Write-Warning "the run reported an error; see $errorPath" }
$resultPath = Join-Path $out "$name.json"
& $adb pull "$base/results/$name.json" $resultPath
if ($LASTEXITCODE -ne 0) {
    throw "no result file was produced; see $name.error in eval\results if it was pulled"
}
Write-Host "result: $resultPath"
Write-Host "report: .\gradlew.bat :eval:speechReport -PspeechResult=$resultPath -PspeechReportOut=$(Join-Path $out "$name.md")"
