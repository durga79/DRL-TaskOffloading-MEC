@echo off
REM Setup Dependencies for DRL Task Offloading Project
REM This script downloads and sets up the required JAR dependencies

echo =============================================
echo   DRL Task Offloading Dependencies Setup
echo =============================================
echo.

REM Change to the project directory
cd /d "%~dp0"

REM Create necessary directories
if not exist "iFogSim\jars" mkdir "iFogSim\jars"
if not exist "lib" mkdir "lib"

echo Downloading required dependencies...
echo.

REM Download CloudSim 3.0.3
if not exist "iFogSim\jars\cloudsim-3.0.3.jar" (
    echo Downloading CloudSim 3.0.3...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/cloudbus/cloudsim/cloudsim/3.0.3/cloudsim-3.0.3.jar' -OutFile 'iFogSim\jars\cloudsim-3.0.3.jar'}"
    if %errorlevel% neq 0 (
        echo Error downloading CloudSim. Please check your internet connection.
        pause
        exit /b 1
    )
    echo   ✓ CloudSim 3.0.3 downloaded
) else (
    echo   ✓ CloudSim 3.0.3 already exists
)

REM Download Commons Math3 3.5
if not exist "iFogSim\jars\commons-math3-3.5.jar" (
    echo Downloading Commons Math3 3.5...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/apache/commons/commons-math3/3.5/commons-math3-3.5.jar' -OutFile 'iFogSim\jars\commons-math3-3.5.jar'}"
    if %errorlevel% neq 0 (
        echo Error downloading Commons Math3. Please check your internet connection.
        pause
        exit /b 1
    )
    echo   ✓ Commons Math3 3.5 downloaded
) else (
    echo   ✓ Commons Math3 3.5 already exists
)

REM Download Guava 18.0
if not exist "iFogSim\jars\guava-18.0.jar" (
    echo Downloading Guava 18.0...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/com/google/guava/guava/18.0/guava-18.0.jar' -OutFile 'iFogSim\jars\guava-18.0.jar'}"
    if %errorlevel% neq 0 (
        echo Error downloading Guava. Please check your internet connection.
        pause
        exit /b 1
    )
    echo   ✓ Guava 18.0 downloaded
) else (
    echo   ✓ Guava 18.0 already exists
)

REM Download JSON Simple 1.1.1
if not exist "iFogSim\jars\json-simple-1.1.1.jar" (
    echo Downloading JSON Simple 1.1.1...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/com/googlecode/json-simple/json-simple/1.1.1/json-simple-1.1.1.jar' -OutFile 'iFogSim\jars\json-simple-1.1.1.jar'}"
    if %errorlevel% neq 0 (
        echo Error downloading JSON Simple. Please check your internet connection.
        pause
        exit /b 1
    )
    echo   ✓ JSON Simple 1.1.1 downloaded
) else (
    echo   ✓ JSON Simple 1.1.1 already exists
)

REM Download JFreeChart libraries for chart generation
if not exist "lib\jfreechart-1.5.3.jar" (
    echo Downloading JFreeChart 1.5.3...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/jfree/jfreechart/1.5.3/jfreechart-1.5.3.jar' -OutFile 'lib\jfreechart-1.5.3.jar'}"
    if %errorlevel% neq 0 (
        echo Error downloading JFreeChart. Please check your internet connection.
        pause
        exit /b 1
    )
    echo   ✓ JFreeChart 1.5.3 downloaded
) else (
    echo   ✓ JFreeChart 1.5.3 already exists
)

if not exist "lib\jcommon-1.0.24.jar" (
    echo Downloading JCommon 1.0.24...
    powershell -Command "& {[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://repo1.maven.org/maven2/org/jfree/jcommon/1.0.24/jcommon-1.0.24.jar' -OutFile 'lib\jcommon-1.0.24.jar'}"
    if %errorlevel% neq 0 (
        echo Error downloading JCommon. Please check your internet connection.
        pause
        exit /b 1
    )
    echo   ✓ JCommon 1.0.24 downloaded
) else (
    echo   ✓ JCommon 1.0.24 already exists
)

echo.
echo =============================================
echo        Dependencies Setup Complete!
echo =============================================
echo.
echo All required JAR files have been downloaded to:
echo   - iFogSim\jars\ (simulation dependencies)
echo   - lib\ (chart generation dependencies)
echo.
echo You can now run the simulation batch files:
echo   - run_simulation.bat (single simulation)
echo   - run_all_simulations.bat (all simulations)
echo   - generate_junit_charts.bat (generate charts)
echo.
echo Next steps:
echo   1. Compile the project: javac -d classes -cp "iFogSim\jars\*" src\org\fog\test\drl\*.java
echo   2. Run simulations: run_all_simulations.bat
echo   3. Generate charts: generate_junit_charts.bat
echo.

pause
