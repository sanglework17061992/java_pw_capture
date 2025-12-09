@echo off
echo ==========================================
echo Smart Locator Capture Tool - Build Script
echo ==========================================

REM Check if Java is installed
java -version >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo Error: Java is not installed
    echo Please install Java 17 or higher
    exit /b 1
)

echo Java version check passed

REM Check if Maven wrapper exists
if exist "mvnw.cmd" (
    echo Using Maven wrapper
    set MVN_CMD=mvnw.cmd
) else (
    echo Maven wrapper not found, using system Maven
    set MVN_CMD=mvn
)

REM Clean and package
echo Building project...
call %MVN_CMD% clean package -DskipTests

if %ERRORLEVEL% EQU 0 (
    echo ==========================================
    echo Build successful!
    echo JAR file: target\smart-locator.jar
    echo ==========================================
    echo.
    echo To run the application:
    echo   java -jar target\smart-locator.jar
    echo.
    echo Then open your browser to:
    echo   http://localhost:8080
    echo ==========================================
) else (
    echo ==========================================
    echo Build failed!
    echo ==========================================
    exit /b 1
)
