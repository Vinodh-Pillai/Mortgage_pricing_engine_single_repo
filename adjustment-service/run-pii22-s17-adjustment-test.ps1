param()
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$evidenceDir = Join-Path $projectRoot '.local-harness\evidence\PII-22-S17'
$stdoutPath = Join-Path $evidenceDir 'adjustment-service-test.stdout.log'
$stderrPath = Join-Path $evidenceDir 'adjustment-service-test.stderr.log'
$logPath = Join-Path $evidenceDir 'adjustment-service-test.log'
$exitPath = Join-Path $evidenceDir 'adjustment-service-test.exit'
$donePath = Join-Path $evidenceDir 'adjustment-service-test.done'
Remove-Item -LiteralPath $stdoutPath, $stderrPath, $logPath, $exitPath, $donePath -ErrorAction SilentlyContinue

if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'gradlew.bat'))) {
  'gradlew.bat not found in projects/adjustment-service.' | Out-File -FilePath $stderrPath -Encoding utf8
  $code = 127
} else {
  $previousErrorActionPreference = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  & '.\gradlew.bat' 'test' '--tests' 'com.wcpe.adjustment.AdjustmentEvidenceModuleTest' > $stdoutPath 2> $stderrPath
  $code = $LASTEXITCODE
  $ErrorActionPreference = $previousErrorActionPreference
}

"COMMAND: .\gradlew.bat test --tests com.wcpe.adjustment.AdjustmentEvidenceModuleTest" | Out-File -FilePath $logPath -Encoding utf8
"WORKDIR: projects/adjustment-service" | Out-File -FilePath $logPath -Encoding utf8 -Append
"EXIT: $code" | Out-File -FilePath $logPath -Encoding utf8 -Append
"--- stdout ---" | Out-File -FilePath $logPath -Encoding utf8 -Append
Get-Content -LiteralPath $stdoutPath -ErrorAction SilentlyContinue | Out-File -FilePath $logPath -Encoding utf8 -Append
"--- stderr ---" | Out-File -FilePath $logPath -Encoding utf8 -Append
Get-Content -LiteralPath $stderrPath -ErrorAction SilentlyContinue | Out-File -FilePath $logPath -Encoding utf8 -Append
Set-Content -LiteralPath $exitPath -Value $code -Encoding ascii
Set-Content -LiteralPath $donePath -Value (Get-Date -Format o) -Encoding ascii
exit $code
