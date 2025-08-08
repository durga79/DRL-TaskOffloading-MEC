@echo off
setlocal enabledelayedexpansion
REM DRL Task Offloading Master Simulation Runner for Windows
REM This script runs all simulations, generates reports, and creates charts
REM Combines functionality of run_simulation.bat, run_all_simulations.bat, and generate_junit_charts.bat

echo =============================================
echo   DRL Task Offloading Master Runner
echo =============================================
echo This script will:
echo   1. Run single simulation (LearnableDRLTaskOffloading)
echo   2. Run all simulations with reports
echo   3. Generate JUnit charts
echo =============================================
echo.

REM Change to the project directory
cd /d "%~dp0"

REM Check if required dependencies exist
if not exist "classes" (
    echo Error: Classes directory not found. Please compile the project first.
    echo Run: javac -d classes -cp "target\dependency\*" src\org\fog\test\drl\*.java
    echo Or run: setup_dependencies.bat first
    pause
    exit /b 1
)

REM Check for Maven dependencies and set classpath
if exist "target\dependency" (
    set "CLASSPATH=classes;target\dependency\*"
    echo Using Maven dependencies from target\dependency
) else if exist "iFogSim\jars" (
    set "CLASSPATH=classes;iFogSim\jars\*"
    echo Using iFogSim jars
) else (
    echo Error: No dependencies found. Please run one of the following:
    echo   1. mvn dependency:copy-dependencies
    echo   2. setup_dependencies.bat
    echo Required JARs: cloudsim-3.0.3.jar, commons-math3-3.5.jar, guava-18.0.jar, json-simple-1.1.1.jar
    pause
    exit /b 1
)

REM Set Java logging properties to disable logs
set JAVA_OPTS=-Djava.util.logging.config.file=NUL
set JAVA_OPTS=%JAVA_OPTS% -Dorg.cloudsim.cloudsim.core.CloudSim.PRINT=false

echo.
echo =============================================
echo   STEP 1: Running Single Simulation
echo =============================================
echo Running LearnableDRLTaskOffloading...
echo.

REM Run the single simulation
java %JAVA_OPTS% -cp "%CLASSPATH%" org.fog.test.drl.LearnableDRLTaskOffloading > temp_results.txt

REM Add a small delay to ensure file is fully written and closed
timeout /t 2 /nobreak >nul

REM Display only the results section
echo Displaying simulation results:
echo.
findstr /C:"Simulation completed" temp_results.txt >nul 2>&1
if %errorlevel% equ 0 (
    REM Find the line number where "Simulation completed" appears
    for /f "delims=:" %%i in ('findstr /n /C:"Simulation completed" temp_results.txt') do set "linenum=%%i"
    REM Display from that line onwards
    if defined linenum (
        more +!linenum! temp_results.txt
    ) else (
        type temp_results.txt
    )
) else (
    REM If "Simulation completed" not found, display the entire file
    type temp_results.txt
)

REM Add a small delay before deleting the file
timeout /t 1 /nobreak >nul

REM Clean up temporary file - use /F to force deletion if needed
del /F temp_results.txt 2>nul
if exist temp_results.txt (
    echo Warning: Could not delete temporary file. Will continue anyway.
)

echo.
echo =============================================
echo   STEP 2: Running All Simulations
echo =============================================
echo.

REM Create timestamp for this run using PowerShell (more reliable)
for /f "usebackq delims=" %%i in (`powershell -Command "Get-Date -Format 'yyyyMMdd_HHmmss'"`) do set "TIMESTAMP=%%i"

set "RESULTS_DIR=.\results\data\%TIMESTAMP%"
if not exist ".\results" mkdir ".\results"
if not exist ".\results\data" mkdir ".\results\data"
if not exist ".\results\charts" mkdir ".\results\charts"
if not exist ".\results\metrics" mkdir ".\results\metrics"
if not exist "%RESULTS_DIR%" mkdir "%RESULTS_DIR%"
if not exist ".\results\charts\%TIMESTAMP%" mkdir ".\results\charts\%TIMESTAMP%"
if not exist ".\results\metrics\%TIMESTAMP%" mkdir ".\results\metrics\%TIMESTAMP%"

echo Starting comprehensive simulations at %date% %time%
echo Results will be saved to: %RESULTS_DIR%
echo.

REM Run Simple DRL Simulation
echo Running Simple DRL Task Offloading...
java %JAVA_OPTS% -cp "%CLASSPATH%" org.fog.test.drl.SimpleDRLTaskOffloading > "%RESULTS_DIR%\simple_drl_results.txt" 2>&1
echo   ✓ Simple DRL completed

REM Add a delay to ensure file is fully written and closed
timeout /t 2 /nobreak >nul

REM Extract key metrics for JUnit
findstr /C:"RESULTS" "%RESULTS_DIR%\simple_drl_results.txt" > "%RESULTS_DIR%\simple_drl_metrics.txt" 2>nul

REM Run Learnable DRL Simulation
echo Running Learnable DRL Task Offloading...
java %JAVA_OPTS% -cp "%CLASSPATH%" org.fog.test.drl.LearnableDRLTaskOffloading > "%RESULTS_DIR%\learnable_drl_results.txt" 2>&1
echo   ✓ Learnable DRL completed

REM Add a delay to ensure file is fully written and closed
timeout /t 2 /nobreak >nul

REM Extract key metrics for JUnit
findstr /C:"RESULTS" "%RESULTS_DIR%\learnable_drl_results.txt" > "%RESULTS_DIR%\learnable_drl_metrics.txt" 2>nul

REM Run Full DRL Simulation if available
if exist "classes\org\fog\test\drl\FullDRLTaskOffloading.class" (
    echo Running Full DRL Task Offloading...
    java %JAVA_OPTS% -cp "%CLASSPATH%" org.fog.test.drl.FullDRLTaskOffloading > "%RESULTS_DIR%\full_drl_results.txt" 2>&1
    echo   ✓ Full DRL completed
    
    REM Add a delay to ensure file is fully written and closed
    timeout /t 2 /nobreak >nul
    
    REM Extract key metrics for JUnit
    findstr /C:"RESULTS" "%RESULTS_DIR%\full_drl_results.txt" > "%RESULTS_DIR%\full_drl_metrics.txt" 2>nul
)

REM Generate JUnit-style XML reports
(
echo ^<?xml version="1.0" encoding="UTF-8"?^>
echo ^<testsuites^>
echo   ^<testsuite name="DRLTaskOffloading" tests="3" failures="0" errors="0" skipped="0" timestamp="%date% %time%"^>
echo     ^<testcase classname="org.fog.test.drl.SimpleDRLTaskOffloading" name="testSimpleDRL"^>
echo       ^<system-out^>
type "%RESULTS_DIR%\simple_drl_metrics.txt" 2>nul
echo       ^</system-out^>
echo     ^</testcase^>
echo     ^<testcase classname="org.fog.test.drl.LearnableDRLTaskOffloading" name="testLearnableDRL"^>
echo       ^<system-out^>
type "%RESULTS_DIR%\learnable_drl_metrics.txt" 2>nul
echo       ^</system-out^>
echo     ^</testcase^>
echo     ^<testcase classname="org.fog.test.drl.FullDRLTaskOffloading" name="testFullDRL"^>
echo       ^<system-out^>
if exist "%RESULTS_DIR%\full_drl_metrics.txt" type "%RESULTS_DIR%\full_drl_metrics.txt"
echo       ^</system-out^>
echo     ^</testcase^>
echo   ^</testsuite^>
echo ^</testsuites^>
) > "%RESULTS_DIR%\junit_report.xml"

REM Generate metrics extraction script for chart generation
(
echo #!/usr/bin/env python3
echo """
echo Metrics Extraction Script for DRL Task Offloading Simulations
echo Extracts key performance metrics from simulation results and generates CSV files
echo """
echo import os
echo import re
echo import json
echo import csv
echo.
echo results_dir = "%RESULTS_DIR%"
echo metrics = {
echo     'execution_time': {},
echo     'energy_consumption': {},
echo     'network_usage': {}
echo }
echo.
echo def extract_metrics^(filename, simulation_type^):
echo     with open^(filename, 'r'^) as f:
echo         content = f.read^(^)
echo         
echo         # Extract execution time
echo         time_match = re.search^(r'EXECUTION TIME : ^(\\d+^)', content^)
echo         if time_match:
echo             metrics['execution_time'][simulation_type] = int^(time_match.group^(1^)^)
echo         
echo         # Extract energy consumption
echo         energy_matches = re.findall^(r'^(\\w+-?\\w*-?\\w*^) : Energy Consumed = ^([\\d\\.]+^)', content^)
echo         total_energy = sum^(float^(energy^) for _, energy in energy_matches^)
echo         metrics['energy_consumption'][simulation_type] = total_energy
echo         
echo         # Extract network usage
echo         network_match = re.search^(r'Total network usage = ^([\\d\\.]+^)', content^)
echo         if network_match:
echo             metrics['network_usage'][simulation_type] = float^(network_match.group^(1^)^)
echo.
echo # Process simulation results
echo if os.path.exists^(f"{results_dir}/simple_drl_results.txt"^):
echo     extract_metrics^(f"{results_dir}/simple_drl_results.txt", "Simple"^)
echo.
echo if os.path.exists^(f"{results_dir}/learnable_drl_results.txt"^):
echo     extract_metrics^(f"{results_dir}/learnable_drl_results.txt", "Learnable"^)
echo.
echo if os.path.exists^(f"{results_dir}/full_drl_results.txt"^):
echo     extract_metrics^(f"{results_dir}/full_drl_results.txt", "Full"^)
echo.
echo # Save metrics as CSV
echo for metric_name, metric_data in metrics.items^(^):
echo     with open^(f"{results_dir}/{metric_name}.csv", 'w', newline='''^) as csvfile:
echo         writer = csv.writer^(csvfile^)
echo         writer.writerow^(['Simulation Type', metric_name]^)
echo         for sim_type, value in metric_data.items^(^):
echo             writer.writerow^([sim_type, value]^)
echo.
echo # Save all metrics as JSON for chart generation
echo with open^(f"{results_dir}/all_metrics.json", 'w'^) as jsonfile:
echo     json.dump^(metrics, jsonfile, indent=2^)
echo.
echo print^(f"Metrics extracted and saved to {results_dir}"^)
) > "%RESULTS_DIR%\extract_metrics.py"

REM Execute the metrics extraction if Python is available
python --version >nul 2>&1
if %errorlevel% equ 0 (
    echo Extracting metrics for charts...
    python "%RESULTS_DIR%\extract_metrics.py"
    echo   ✓ Metrics extracted and CSV files created
) else (
    python3 --version >nul 2>&1
    if %errorlevel% equ 0 (
        echo Extracting metrics for charts...
        python3 "%RESULTS_DIR%\extract_metrics.py"
        echo   ✓ Metrics extracted and CSV files created
    ) else (
        echo Python not found. To extract metrics for charts, install Python and run:
        echo   python "%RESULTS_DIR%\extract_metrics.py"
    )
)

echo.
echo =============================================
echo   STEP 3: Generating JUnit Charts
echo =============================================
echo.

REM Check if results directory exists
if not exist ".\results\data" (
    echo Error: No simulation results found. Simulations may have failed.
    goto :end_charts
)

REM Find the most recent results directory (use the one we just created)
set "LATEST_DIR=%RESULTS_DIR%"

echo Using results from: %LATEST_DIR%

REM Create lib directory if it doesn't exist
if not exist "lib" mkdir lib

REM Check if JFreeChart libraries are already downloaded
if not exist "lib\jfreechart-1.5.3.jar" (
    echo Downloading JFreeChart libraries...
    
    REM Download JFreeChart using PowerShell
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/jfree/jfreechart/1.5.3/jfreechart-1.5.3.jar' -OutFile 'lib\jfreechart-1.5.3.jar'}"
    
    if %errorlevel% neq 0 (
        echo Error: Failed to download JFreeChart. Please check your internet connection.
        goto :end_charts
    )
    
    echo JFreeChart downloaded successfully.
)

if not exist "lib\jcommon-1.0.24.jar" (
    echo Downloading JCommon library...
    
    REM Download JCommon (dependency for JFreeChart)
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/jfree/jcommon/1.0.24/jcommon-1.0.24.jar' -OutFile 'lib\jcommon-1.0.24.jar'}"
    
    if %errorlevel% neq 0 (
        echo Error: Failed to download JCommon. Please check your internet connection.
        goto :end_charts
    )
    
    echo JCommon downloaded successfully.
)

REM Check for Maven dependencies and set classpath for chart generation
if exist "target\dependency" (
    set "BASE_CLASSPATH=classes;target\dependency\*"
    echo Using Maven dependencies for chart generation
) else if exist "iFogSim\jars" (
    set "BASE_CLASSPATH=classes;iFogSim\jars\*"
    echo Using iFogSim jars for chart generation
) else (
    echo Error: No dependencies found for chart generation.
    goto :end_charts
)

REM Compile the chart generator
echo Compiling chart generator...

REM Check if ChartGenerator.java exists
if not exist "src\org\fog\utils\ChartGenerator.java" (
    echo Error: ChartGenerator.java not found.
    echo Expected path: src\org\fog\utils\ChartGenerator.java
    goto :end_charts
)

REM Ensure src directory exists in classpath
set "CHART_CLASSPATH=%BASE_CLASSPATH%;lib\jfreechart-1.5.3.jar;lib\jcommon-1.0.24.jar"

REM Create a temporary batch file for compilation to avoid syntax issues
@echo javac -Xlint:unchecked -d classes -cp "%CHART_CLASSPATH%" src\org\fog\utils\ChartGenerator.java > compile_chart.cmd

REM Execute the temporary batch file and redirect errors
call compile_chart.cmd 2>chart_compile_errors.txt

if %errorlevel% neq 0 (
    echo Error: Failed to compile chart generator.
    echo Make sure Java Development Kit (JDK) is installed and in your PATH.
    echo Compilation errors:
    type chart_compile_errors.txt
    del chart_compile_errors.txt
    goto :end_charts
)

del chart_compile_errors.txt 2>nul

REM Check if metrics file exists before running chart generator
if not exist "%LATEST_DIR%\all_metrics.json" (
    echo Error: Metrics file not found at %LATEST_DIR%\all_metrics.json
    echo Metrics extraction may have failed. Skipping chart generation.
    goto :end_charts
)

REM Add a delay to ensure metrics file is fully written and accessible
timeout /t 2 /nobreak >nul

REM Run the chart generator with error output captured
echo Generating JUnit charts from metrics...

REM Create a temporary batch file for chart generation to avoid syntax issues
@echo java -cp "%CHART_CLASSPATH%" org.fog.utils.ChartGenerator "%LATEST_DIR%\all_metrics.json" > run_chart.cmd

REM Execute the temporary batch file and redirect errors
call run_chart.cmd 2>chart_errors.txt

if %errorlevel% equ 0 (
    set "CHARTS_DIR=.\results\charts\%TIMESTAMP%"
    echo.
    echo JUnit-style charts generated successfully!
    echo Charts are available in: %CHARTS_DIR%
    echo.
    echo Available charts:
    echo - %CHARTS_DIR%\execution_time_chart.png
    echo - %CHARTS_DIR%\energy_consumption_chart.png
    echo - %CHARTS_DIR%\network_usage_chart.png
) else (
    echo Error: Failed to generate charts. Error details:
    type chart_errors.txt
    echo.
    echo Please check if the metrics file format is correct and all dependencies are available.
)

del chart_errors.txt 2>nul

:end_charts

echo.
echo =============================================
echo        All Operations Completed!
echo =============================================
echo.
echo Summary:
echo   1. ✓ Single simulation executed
echo   2. ✓ All simulations completed with reports
echo   3. ✓ JUnit charts generated (if successful)
echo.
echo Results are available in: %RESULTS_DIR%
echo JUnit report: %RESULTS_DIR%\junit_report.xml
echo.
echo Files generated:
echo   - Simulation results (*.txt)
echo   - Metrics files (*.csv, *.json)
echo   - JUnit XML report
echo   - Visual charts (*.png)
echo.

pause
