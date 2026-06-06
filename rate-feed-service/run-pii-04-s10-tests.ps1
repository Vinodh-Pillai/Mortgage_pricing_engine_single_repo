$ErrorActionPreference = "Stop"
$ServiceDir = $PSScriptRoot
$EvidenceDir = Join-Path (Resolve-Path (Join-Path $ServiceDir "..\..\..")).Path ".local-harness\evidence\PII-04-S10"
New-Item -ItemType Directory -Path $EvidenceDir -Force | Out-Null
$Log = Join-Path $EvidenceDir "rate-feed-test.log"
$ExitFile = Join-Path $EvidenceDir "pii-04-s10-test.exit"
$DoneFile = Join-Path $EvidenceDir "pii-04-s10-test.done"
Remove-Item -Path $Log, $ExitFile, $DoneFile -ErrorAction SilentlyContinue
$logq = Join-Path $ServiceDir "pii-04-s10-run.log"
cmd.exe /d /s /c "cd /d ""$ServiceDir"" .\gradlew.bat test --no-daemon --console=plain > ""$logq"" 2>&1"
$code = $LASTEXITCODE
Copy-Item -Path $logq -Destination $Log -Force
Set-Content -Path $ExitFile -Value $code -Encoding ASCII
Set-Content -Path $DoneFile -Value (Get-Date -Format o) -Encoding ASCII
exit $code
