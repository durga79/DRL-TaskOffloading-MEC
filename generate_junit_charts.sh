#!/bin/bash

# JUnit Charts Generator for DRL Task Offloading Simulations
# Downloads JFreeChart library if needed and generates comparison charts

# Set current directory to project root
cd $(dirname $0)

# Check if results directory exists
if [ ! -d "./results/data" ]; then
  echo "Error: No simulation results found. Run simulations first."
  exit 1
fi

# Find the most recent results directory
LATEST_DIR=$(find ./results/data -maxdepth 1 -type d | sort | tail -1)

if [ "$LATEST_DIR" = "./results/data" ]; then
  echo "Error: No simulation results found. Run simulations first."
  exit 1
fi

# Create lib directory if it doesn't exist
mkdir -p lib

# Check if JFreeChart libraries are already downloaded
if [ ! -f "lib/jfreechart-1.5.3.jar" ] || [ ! -f "lib/jcommon-1.0.24.jar" ]; then
  echo "Downloading JFreeChart libraries..."
  
  # Download JFreeChart
  curl -L -o lib/jfreechart-1.5.3.jar https://repo1.maven.org/maven2/org/jfree/jfreechart/1.5.3/jfreechart-1.5.3.jar
  
  # Download JCommon (dependency for JFreeChart)
  curl -L -o lib/jcommon-1.0.24.jar https://repo1.maven.org/maven2/org/jfree/jcommon/1.0.24/jcommon-1.0.24.jar
  
  echo "Libraries downloaded successfully."
fi

# Compile the chart generator
echo "Compiling chart generator..."
javac -d classes -cp classes:iFogSim/jars/*:lib/jfreechart-1.5.3.jar:lib/jcommon-1.0.24.jar src/org/fog/utils/ChartGenerator.java

if [ $? -ne 0 ]; then
  echo "Error: Failed to compile chart generator."
  exit 1
fi

# Run the chart generator
echo "Generating JUnit charts from metrics..."
java -cp classes:iFogSim/jars/*:lib/jfreechart-1.5.3.jar:lib/jcommon-1.0.24.jar org.fog.utils.ChartGenerator "${LATEST_DIR}/all_metrics.json"

if [ $? -eq 0 ]; then
  CHARTS_DIR="${LATEST_DIR/\/data\//\/charts\/}"
  echo ""
  echo "JUnit-style charts generated successfully!"
  echo "Charts are available in: ${CHARTS_DIR}"
  echo ""
  echo "Available charts:"
  echo "- ${CHARTS_DIR}/execution_time_chart.png"
  echo "- ${CHARTS_DIR}/energy_consumption_chart.png"
  echo "- ${CHARTS_DIR}/network_usage_chart.png"
  echo ""
  echo "Use these charts to compare different simulation approaches."
fi
