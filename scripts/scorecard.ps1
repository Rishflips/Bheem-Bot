param(
    [Parameter(Mandatory)] [string] $ModelId,
    [Parameter(Mandatory)] [string] $ModelFile,
    [string] $Primary = 'gemma-4-e2b',
    [string] $Device = "scorecard-$(Get-Date -Format 'yyyyMMdd')",
    [ValidateSet('both', 'cpu', 'gpu')] [string] $Backends = 'both',
    [int] $TimeoutMinutes = 120
)
# Runs one model through the phone benchmark on the assistant development set and the held-out set, on each backend, with the shipped
# planner prompt, then writes docs/scorecards/<ModelId>.md with the relative swap rule against the primary model.
# The held-out results are scored by code and appear only as aggregates in the scorecard; this script prints no case.
# Needs the phone connected and unlocked, the bench app built (.\gradlew.bat :bench:assembleDebug) and the model file on this computer.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$backendList = if ($Backends -eq 'both') { @('gpu', 'cpu') } else { @($Backends) }

foreach ($backend in $backendList) {
    foreach ($set in @('assistant', 'heldout')) {
        Write-Host "scorecard: $ModelId on $backend, set $set"
        & (Join-Path $PSScriptRoot 'bench-device.ps1') -Device $Device -ModelId $ModelId -ModelFile $ModelFile -Backend $backend -DataSet $set -Prompt line2 -Benchmark off -TimeoutMinutes $TimeoutMinutes
    }
}

& (Join-Path $root 'gradlew.bat') ':eval:scorecard' "-PscorecardModel=$ModelId" "-PscorecardPrimary=$Primary"
if ($LASTEXITCODE -ne 0) { throw 'the scorecard task failed' }
Write-Host "written: $(Join-Path $root "docs\scorecards\$ModelId.md")"
