$ErrorActionPreference = "Stop"
$log = Join-Path $PSScriptRoot "pii-04-s05-test.log"
$exitFile = Join-Path $PSScriptRoot "pii-04-s05-test.exit"
$doneFile = Join-Path $PSScriptRoot "pii-04-s05-test.done"
Remove-Item -LiteralPath $log, $exitFile, $doneFile -ErrorAction SilentlyContinue
cmd.exe /d /s /c ".\gradlew.bat test --tests *RateSheetNormalizationPolicyTest > pii-04-s05-test.log 2>&1"
$code = $LASTEXITCODE
Set-Content -LiteralPath $exitFile -Value $code -Encoding ASCII
Set-Content -LiteralPath $doneFile -Value (Get-Date -Format o) -Encoding ASCII
exit $code
