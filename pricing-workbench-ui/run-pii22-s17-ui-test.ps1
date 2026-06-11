param()
$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$evidenceDir = Join-Path $projectRoot '.local-harness\evidence\PII-22-S17'
$stdoutPath = Join-Path $evidenceDir 'ui-test.stdout.log'
$stderrPath = Join-Path $evidenceDir 'ui-test.stderr.log'
$logPath = Join-Path $evidenceDir 'ui-test.log'
$exitPath = Join-Path $evidenceDir 'ui-test.exit'
$donePath = Join-Path $evidenceDir 'ui-test.done'
Remove-Item -LiteralPath $stdoutPath, $stderrPath, $logPath, $exitPath, $donePath -ErrorAction SilentlyContinue

if (-not (Test-Path -LiteralPath (Join-Path $PSScriptRoot 'package.json'))) {
  'package.json not found in projects/pricing-workbench-ui.' | Out-File -FilePath $stderrPath -Encoding utf8
  $code = 127
} else {
  $previousErrorActionPreference = $ErrorActionPreference
  $ErrorActionPreference = 'Continue'
  & 'npm.cmd' 'test' '--' 'App.test.tsx' '-t' 'renders adjustment ids, fact refs, sources, conflicts, compensation hooks, summaries, and blocked config state' > $stdoutPath 2> $stderrPath
  $code = $LASTEXITCODE
  $ErrorActionPreference = $previousErrorActionPreference
}

"COMMAND: npm.cmd test -- App.test.tsx -t 'renders adjustment ids, fact refs, sources, conflicts, compensation hooks, summaries, and blocked config state'" | Out-File -FilePath $logPath -Encoding utf8
"WORKDIR: projects/pricing-workbench-ui" | Out-File -FilePath $logPath -Encoding utf8 -Append
"EXIT: $code" | Out-File -FilePath $logPath -Encoding utf8 -Append
"--- stdout ---" | Out-File -FilePath $logPath -Encoding utf8 -Append
Get-Content -LiteralPath $stdoutPath -ErrorAction SilentlyContinue | Out-File -FilePath $logPath -Encoding utf8 -Append
"--- stderr ---" | Out-File -FilePath $logPath -Encoding utf8 -Append
Get-Content -LiteralPath $stderrPath -ErrorAction SilentlyContinue | Out-File -FilePath $logPath -Encoding utf8 -Append
Set-Content -LiteralPath $exitPath -Value $code -Encoding ascii
Set-Content -LiteralPath $donePath -Value (Get-Date -Format o) -Encoding ascii
exit $code
