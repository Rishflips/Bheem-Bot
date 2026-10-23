param(
    [string] $Manifest,
    [string] $OutDir,
    [string] $Speaker = 'S01',
    [string] $Voice = 'Microsoft Zira Desktop',
    [int] $Limit = 12,
    [int] $Rate = 0
)
# Makes a small synthetic-speech set with a Windows voice, to smoke-test the audio path on the phone before human
# recordings exist. It reuses one speaker's take IDs from the manifest. The voice is US English and clean, so the numbers
# say nothing about Indian accents or noise, and the condition labels (quiet, noisy, far) do not apply to this audio.
# Output: <OutDir>\manifest.jsonl and <OutDir>\audio\<take ID>.wav (16 kHz mono 16-bit).
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
if (-not $Manifest) { $Manifest = Join-Path $root 'eval\recordings\manifest.jsonl' }
if (-not $OutDir) { $OutDir = Join-Path $root 'eval\recordings-synthetic' }
if (-not (Test-Path $Manifest)) { throw "manifest not found: $Manifest" }

$lines = @(Get-Content $Manifest -Encoding UTF8 | Where-Object { $_.Trim() })
$takes = @($lines | ForEach-Object { $_ | ConvertFrom-Json } | Where-Object { $_.speaker -eq $Speaker })
if ($takes.Count -eq 0) { throw "no takes for speaker $Speaker in $Manifest" }
$count = [Math]::Min($Limit, $takes.Count)
$picked = @(0..($count - 1) | ForEach-Object { $takes[[int][Math]::Floor($_ * $takes.Count / $count)] })

Add-Type -AssemblyName System.Speech
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $synth.SelectVoice($Voice)
    $synth.Rate = $Rate
    $format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(
        16000, [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
    $audioDir = Join-Path $OutDir 'audio'
    New-Item -ItemType Directory -Force $audioDir | Out-Null
    foreach ($take in $picked) {
        $synth.SetOutputToWaveFile((Join-Path $audioDir "$($take.id).wav"), $format)
        $synth.Speak($take.text)
        $synth.SetOutputToNull()
    }
} finally {
    $synth.Dispose()
}
$manifestLines = @($picked | ForEach-Object { $_ | ConvertTo-Json -Compress })
[System.IO.File]::WriteAllLines((Join-Path $OutDir 'manifest.jsonl'), $manifestLines, (New-Object System.Text.UTF8Encoding($false)))
Write-Host "wrote $($picked.Count) synthetic takes for $Speaker with '$Voice' into $OutDir"
