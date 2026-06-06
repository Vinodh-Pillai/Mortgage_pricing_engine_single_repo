@rem Local module wrapper shim for the story harness.
@rem Runs Gradle from this module working directory while reusing the checked-in wrapper jar from scenario-service.
@echo off
set DIR=%~dp0
set WRAPPER_JAR=%DIR%..\scenario-service\gradle\wrapper\gradle-wrapper.jar
if not exist "%WRAPPER_JAR%" (
  echo Gradle wrapper jar not found: %WRAPPER_JAR% 1>&2
  exit /b 127
)
java -Dorg.gradle.appname=gradlew -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
