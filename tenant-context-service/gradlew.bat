@echo off
setlocal
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
where gradle.bat >nul 2>nul
if %ERRORLEVEL%==0 (
  call gradle.bat %*
  exit /b %ERRORLEVEL%
)
where gradle >nul 2>nul
if %ERRORLEVEL%==0 (
  call gradle %*
  exit /b %ERRORLEVEL%
)
echo Gradle executable not found. Install Gradle or replace this service-local wrapper with a full Gradle wrapper distribution.
exit /b 1
