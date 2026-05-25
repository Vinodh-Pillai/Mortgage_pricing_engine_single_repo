@echo off
setlocal
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle is not installed or not on PATH. Install Gradle 8.10.2 or generate a full wrapper with gradle wrapper --gradle-version 8.10.2.
  exit /b 1
)
gradle %*
