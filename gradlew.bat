@echo off
if exist "%~dp0work\tools\temurin-21\jdk-21.0.11+10\bin\java.exe" (
  set "JAVA_HOME=%~dp0work\tools\temurin-21\jdk-21.0.11+10"
  set "Path=%JAVA_HOME%\bin;%Path%"
)
if exist "%~dp0work\tools\gradle-8.14.3\bin\gradle.bat" (
  call "%~dp0work\tools\gradle-8.14.3\bin\gradle.bat" %*
  exit /b %errorlevel%
)
where gradle >nul 2>nul
if %errorlevel%==0 (
  gradle %*
  exit /b %errorlevel%
)
echo Gradle is not installed and no Gradle Wrapper JAR is bundled. Install Gradle or run "gradle wrapper" once. 1>&2
exit /b 1
