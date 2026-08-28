@echo off
setlocal
set "PROJECT_DIR=%~dp0"
set "GRADLE_VERSION=8.10.2"
set "GRADLE_HOME=%PROJECT_DIR%.gradle-dist\gradle-%GRADLE_VERSION%"
set "GRADLE_ZIP=%PROJECT_DIR%.gradle-dist\gradle-%GRADLE_VERSION%-bin.zip"

if not exist "%GRADLE_HOME%\bin\gradle.bat" (
  echo [HarbourPVP] Gradle %GRADLE_VERSION% not found. Downloading it automatically...
  if not exist "%PROJECT_DIR%.gradle-dist" mkdir "%PROJECT_DIR%.gradle-dist"
  powershell -NoProfile -ExecutionPolicy Bypass -Command "$u='https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip'; $o='%GRADLE_ZIP%'; Invoke-WebRequest -UseBasicParsing -Uri $u -OutFile $o"
  if errorlevel 1 (
    echo Failed to download Gradle. Check your internet connection.
    exit /b 1
  )
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -LiteralPath '%GRADLE_ZIP%' -DestinationPath '%PROJECT_DIR%.gradle-dist' -Force"
  if errorlevel 1 (
    echo Failed to extract Gradle.
    exit /b 1
  )
)

call "%GRADLE_HOME%\bin\gradle.bat" %*
exit /b %errorlevel%
