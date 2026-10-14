param([string]$Scenario = 'docs/refactor/tradingview-rea-scenario.json')
$resultText = & npx.cmd -y rea-agents@6.4.0 capture-browser-scenario $Scenario --format json
if ($LASTEXITCODE -ne 0) { throw 'REA capture command failed.' }
$result = $resultText | ConvertFrom-Json
if (-not $result.evidence_id) { throw 'REA returned no Evidence ID.' }
$result | Select-Object evidence_id, confidence
foreach ($step in $result.normalized_result.steps) {
    Write-Output ($step.step_id + ': ' + $step.status)
    if ($step.error) { $step.error | ConvertTo-Json -Depth 5 }
    if ($step.step_id -notin @('scenario_start', 'initial_chart', 'close_trend', 'close_templates')) {
        $step.artifacts.accessibility.value.text -split "`n" | Select-Object -Last 85
    }
}
if ($result.normalized_result.steps | Where-Object { $_.status -ne 'completed' }) {
    throw 'REA scenario contains an incomplete step; inspect the reported error.'
}
