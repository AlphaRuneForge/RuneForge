@echo off
setlocal EnableExtensions
cd /d "%~dp0.."
title Rune Forge v1.0.1

set "DUMP=%CD%\RuneForge-Dump.log"

> "%DUMP%" echo ============================================================
>>"%DUMP%" echo Rune Forge v1.0.1
>>"%DUMP%" echo Started: %date% %time%
>>"%DUMP%" echo Folder: %CD%
>>"%DUMP%" echo ============================================================

powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%CD%\launcher\Start-RuneForge.ps1" >>"%DUMP%" 2>&1
set "RC=%ERRORLEVEL%"

if not "%RC%"=="0" (
    echo.
    echo Rune Forge failed to start. Error code: %RC%
    echo Diagnostic log:
    echo   %DUMP%
    echo.
    pause
    exit /b %RC%
)

exit /b 0
