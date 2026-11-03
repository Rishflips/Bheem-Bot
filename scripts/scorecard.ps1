param(
    [Parameter(Mandatory)] [string] $ModelId,
    [Parameter(Mandatory)] [string] $ModelFile,
    [string] $Primary = 'gemma-4-e2b',
    [string] $PrimaryDevice = 'v38',
    [string] $Device = "scorecard-$(Get-Date -Format 'yyyyMMdd')",
    [ValidateSet('both', 'cpu', 'gpu')] [string] $Backends = 'both',
    [string[]] $Sets = @('assistant', 'heldout', 'standard'),
    [ValidateSet('on', 'off')] [string] $AudioEngine = 'on',
    [int] $TimeoutMinutes = 120
)
# Runs one model through the phone benchmark on the assistant development set, the held-out set and the full English set (commands,
# notifications and injection attempts), on each backend, with the shipped planner prompt, then writes docs/scorecards/<ModelId>.md with
# the relative swap rule against the primary model's saved runs (device label prefix $PrimaryDevice).
# -AudioEngine on loads the audio encoder, as the app does for a model whose profile has AUDIO_IN; use off for a model without audio.
# The held-out results are scored by code and appear only as aggregates in the scorecard; this script prints no case.
# Needs the phone connected and unlocked, the bench app built (.\gradlew.bat :bench:assembleDebug) and the model file on this computer.
# Known limit: the bench always uses the shipped planner prompt (line2); a candidate's own prompt id is not measured yet.
# adb writes progress to stderr, which Windows PowerShell 5.1 turns into errors under 'Stop', so errors are checked by exit code.
$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$backendList = if ($Backends -eq 'both') { @('gpu', 'cpu') } else { @($Backends) }

foreach ($backend in $backendList) {
    foreach ($set in $Sets) {
        Write-Host "scorecard: $ModelId on $backend, set $set"
        $extra = @{}
        if ($set -eq 'standard') { $extra['Language'] = 'en' }
        & (Join-Path $PSScriptRoot 'bench-device.ps1') -Device $Device -ModelId $ModelId -ModelFile $ModelFile -Backend $backend -DataSet $set -Prompt line2 -Benchmark on -AudioEngine $AudioEngine -TimeoutMinutes $TimeoutMinutes @extra
        if (-not $?) { throw "the $set run on $backend failed" }
    }
}

& (Join-Path $root 'gradlew.bat') ':eval:scorecard' "-PscorecardModel=$ModelId" "-PscorecardPrimary=$Primary" "-PscorecardDevice=$Device" "-PscorecardPrimaryDevice=$PrimaryDevice"
if ($LASTEXITCODE -ne 0) { throw 'the scorecard task failed' }
Write-Host "written: $(Join-Path $root "docs\scorecards\$ModelId.md")"
