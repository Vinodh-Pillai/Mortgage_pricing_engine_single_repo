$ErrorActionPreference = 'Stop'
& .\gradlew.bat test --tests com.wcpe.catalog.domain.CatalogServiceIntegrationTest
$exitCode = $LASTEXITCODE
if ($null -eq $exitCode) { $exitCode = 0 }
exit $exitCode
