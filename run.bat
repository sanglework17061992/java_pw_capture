@echo off
echo ==========================================
echo Starting Smart Locator Capture Tool
echo ==========================================

REM Check if JAR exists
if not exist "target\smart-locator.jar" (
    echo Error: smart-locator.jar not found
    echo Please build the project first:
    echo   build.bat
    exit /b 1
)

REM Run the application
echo Starting application...
java -jar target\smart-locator.jar
