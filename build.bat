@echo off
setlocal
cd /d "%~dp0"
if not exist "%JAVA_HOME%\bin\java.exe" (
  java -version >nul 2>&1
  if errorlevel 1 (
    echo Java 21 is required. Install Java 21 first.
    pause
    exit /b 1
  )
)
call gradlew.bat clean build
if errorlevel 1 (
  echo.
  echo BUILD FAILED.
  pause
  exit /b 1
)
echo.
echo ========================================
echo BUILD SUCCESSFUL!
echo JAR: build\libs\HarbourPVP-1.0.0.jar
echo ========================================
pause
