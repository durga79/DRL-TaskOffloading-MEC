@echo off
REM Standalone Chart Generation Batch File
REM This script handles chart generation separately to avoid syntax issues in the main script

echo =============================================
echo   Chart Generation Script
echo =============================================

REM Get parameters
set "RESULTS_DIR=%1"
set "TIMESTAMP=%2"

if "%RESULTS_DIR%"=="" (
    echo Error: Results directory not specified
    exit /b 1
)

if "%TIMESTAMP%"=="" (
    echo Error: Timestamp not specified
    exit /b 1
)

echo Results directory: %RESULTS_DIR%
echo Timestamp: %TIMESTAMP%

REM Create lib directory if it doesn't exist
if not exist "lib" mkdir lib

REM Check if JFreeChart libraries are already downloaded
if not exist "lib\jfreechart-1.5.3.jar" (
    echo Downloading JFreeChart libraries...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/jfree/jfreechart/1.5.3/jfreechart-1.5.3.jar' -OutFile 'lib\jfreechart-1.5.3.jar'}"
    if %errorlevel% neq 0 (
        echo Error: Failed to download JFreeChart
        exit /b 1
    )
)

if not exist "lib\jcommon-1.0.24.jar" (
    echo Downloading JCommon library...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/jfree/jcommon/1.0.24/jcommon-1.0.24.jar' -OutFile 'lib\jcommon-1.0.24.jar'}"
    if %errorlevel% neq 0 (
        echo Error: Failed to download JCommon
        exit /b 1
    )
)

REM Set up classpath
if exist "target\dependency" (
    set "BASE_CP=classes;target\dependency\*"
) else (
    set "BASE_CP=classes;iFogSim\jars\*"
)

REM Check if ChartGenerator.java exists
if not exist "src\org\fog\utils\ChartGenerator.java" (
    echo Error: ChartGenerator.java not found
    exit /b 1
)

REM Compile ChartGenerator
echo Compiling ChartGenerator...
javac -d classes -cp "%BASE_CP%;lib\jfreechart-1.5.3.jar;lib\jcommon-1.0.24.jar" src\org\fog\utils\ChartGenerator.java

if %errorlevel% neq 0 (
    echo Error: Failed to compile ChartGenerator
    exit /b 1
)

echo ChartGenerator compiled successfully!

REM Check if metrics file exists
if not exist "%RESULTS_DIR%\all_metrics.json" (
    echo Error: Metrics file not found at %RESULTS_DIR%\all_metrics.json
    exit /b 1
)

REM Run the chart generator
echo Generating charts from metrics...
java -cp "%BASE_CP%;lib\jfreechart-1.5.3.jar;lib\jcommon-1.0.24.jar" org.fog.utils.ChartGenerator "%RESULTS_DIR%\all_metrics.json"

if %errorlevel% equ 0 (
    echo Charts generated successfully!
    echo Charts are available in: .\results\charts\%TIMESTAMP%
    exit /b 0
) else (
    echo Error: Failed to generate charts
    exit /b 1
)
