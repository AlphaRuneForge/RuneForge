@echo off
where gradle >nul 2>nul
if errorlevel 1 (
  echo Gradle is not installed or not on PATH.
  echo Install Gradle 8.10 or newer, then run gradlew.bat build again.
  exit /b 1
)
gradle %*
