$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$logDir = Join-Path $root "test-results\local-harness\ui-dev"
New-Item -ItemType Directory -Force -Path $logDir | Out-Null

$stdout = Join-Path $logDir "stdout.log"
$stderr = Join-Path $logDir "stderr.log"
$pidFile = Join-Path $logDir "ui-dev.pid"
$env:VITE_BFF_API_BASE_URL = "http://127.0.0.1:18080"
$env:VITE_API_BASE = "http://127.0.0.1:18080"

$psi = New-Object System.Diagnostics.ProcessStartInfo
$viteBin = Join-Path $root "node_modules\vite\bin\vite.js"
$psi.FileName = "node"
$psi.Arguments = "`"$viteBin`" --host 127.0.0.1 --port 3000 --strictPort"
$psi.WorkingDirectory = $root
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true
$process = [System.Diagnostics.Process]::Start($psi)
Set-Content -LiteralPath $pidFile -Value $process.Id -Encoding ASCII

Start-Sleep -Seconds 8
if ($process.HasExited) {
    $process.StandardOutput.ReadToEnd() | Add-Content -LiteralPath $stdout
    $process.StandardError.ReadToEnd() | Add-Content -LiteralPath $stderr
    exit $process.ExitCode
}
exit 0
