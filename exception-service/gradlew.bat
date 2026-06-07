@echo off
setlocal
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle CLI is required because the exception-service local wrapper jar is not present. 1>&2
  exit /b 1
)
gradle %*
exit /b %ERRORLEVEL%
