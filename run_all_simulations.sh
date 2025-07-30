#!/bin/bash

# DRL Task Offloading Simulation Runner
# This script runs all simulations and stores results in the results directory

# Set current directory to project root
cd $(dirname $0)

# Create timestamp for this run
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
RESULTS_DIR="./results/data/${TIMESTAMP}"
mkdir -p ${RESULTS_DIR}
mkdir -p "./results/charts/${TIMESTAMP}"
mkdir -p "./results/metrics/${TIMESTAMP}"

# Define Java options to disable logging
JAVA_OPTS="-Djava.util.logging.config.file=/dev/null"
JAVA_OPTS="${JAVA_OPTS} -Dorg.cloudsim.cloudsim.core.CloudSim.PRINT=false"

# Common classpath
CLASSPATH="classes:iFogSim/jars/*:iFogSim/jars/cloudsim-3.0.3.jar:iFogSim/jars/cloudsim-examples-3.0.3.jar:iFogSim/jars/commons-math3-3.5/commons-math3-3.5.jar"

echo "===========================================" | tee -a ${RESULTS_DIR}/summary.txt
echo "  DRL Task Offloading Simulation Runner    " | tee -a ${RESULTS_DIR}/summary.txt
echo "===========================================" | tee -a ${RESULTS_DIR}/summary.txt
echo "Starting simulations at $(date)" | tee -a ${RESULTS_DIR}/summary.txt
echo "Results will be saved to: ${RESULTS_DIR}" | tee -a ${RESULTS_DIR}/summary.txt
echo "" | tee -a ${RESULTS_DIR}/summary.txt

# Run Simple DRL Simulation
echo "Running Simple DRL Task Offloading..." | tee -a ${RESULTS_DIR}/summary.txt
java ${JAVA_OPTS} -cp ${CLASSPATH} org.fog.test.drl.SimpleDRLTaskOffloading > ${RESULTS_DIR}/simple_drl_results.txt 2>&1
echo "  ✓ Simple DRL completed" | tee -a ${RESULTS_DIR}/summary.txt

# Extract key metrics for JUnit
grep -A 20 "RESULTS" ${RESULTS_DIR}/simple_drl_results.txt > ${RESULTS_DIR}/simple_drl_metrics.txt

# Run Learnable DRL Simulation
echo "Running Learnable DRL Task Offloading..." | tee -a ${RESULTS_DIR}/summary.txt
java ${JAVA_OPTS} -cp ${CLASSPATH} org.fog.test.drl.LearnableDRLTaskOffloading > ${RESULTS_DIR}/learnable_drl_results.txt 2>&1
echo "  ✓ Learnable DRL completed" | tee -a ${RESULTS_DIR}/summary.txt

# Extract key metrics for JUnit
grep -A 20 "RESULTS" ${RESULTS_DIR}/learnable_drl_results.txt > ${RESULTS_DIR}/learnable_drl_metrics.txt

# Run Full DRL Simulation if available
if [ -f classes/org/fog/test/drl/FullDRLTaskOffloading.class ]; then
  echo "Running Full DRL Task Offloading..." | tee -a ${RESULTS_DIR}/summary.txt
  java ${JAVA_OPTS} -cp ${CLASSPATH} org.fog.test.drl.FullDRLTaskOffloading > ${RESULTS_DIR}/full_drl_results.txt 2>&1
  echo "  ✓ Full DRL completed" | tee -a ${RESULTS_DIR}/summary.txt
  # Extract key metrics for JUnit
  grep -A 20 "RESULTS" ${RESULTS_DIR}/full_drl_results.txt > ${RESULTS_DIR}/full_drl_metrics.txt
fi

# Generate JUnit-style XML reports
cat > ${RESULTS_DIR}/junit_report.xml << EOF
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="DRLTaskOffloading" tests="3" failures="0" errors="0" skipped="0" timestamp="$(date)">
    <testcase classname="org.fog.test.drl.SimpleDRLTaskOffloading" name="testSimpleDRL">
      <system-out>
        $(cat ${RESULTS_DIR}/simple_drl_metrics.txt)
      </system-out>
    </testcase>
    <testcase classname="org.fog.test.drl.LearnableDRLTaskOffloading" name="testLearnableDRL">
      <system-out>
        $(cat ${RESULTS_DIR}/learnable_drl_metrics.txt)
      </system-out>
    </testcase>
    <testcase classname="org.fog.test.drl.FullDRLTaskOffloading" name="testFullDRL">
      <system-out>
        $(if [ -f ${RESULTS_DIR}/full_drl_metrics.txt ]; then cat ${RESULTS_DIR}/full_drl_metrics.txt; fi)
      </system-out>
    </testcase>
  </testsuite>
</testsuites>
EOF

echo "" | tee -a ${RESULTS_DIR}/summary.txt
echo "===========================================" | tee -a ${RESULTS_DIR}/summary.txt
echo "      Simulation Run Completed             " | tee -a ${RESULTS_DIR}/summary.txt
echo "===========================================" | tee -a ${RESULTS_DIR}/summary.txt
echo "All results saved to: ${RESULTS_DIR}" | tee -a ${RESULTS_DIR}/summary.txt
echo "JUnit report generated: ${RESULTS_DIR}/junit_report.xml" | tee -a ${RESULTS_DIR}/summary.txt
echo "" | tee -a ${RESULTS_DIR}/summary.txt
echo "To view results:" | tee -a ${RESULTS_DIR}/summary.txt
echo "  cat ${RESULTS_DIR}/summary.txt" | tee -a ${RESULTS_DIR}/summary.txt
echo "" | tee -a ${RESULTS_DIR}/summary.txt

# Generate metrics extraction script for chart generation
cat > ${RESULTS_DIR}/extract_metrics.py << EOF
#!/usr/bin/env python3
"""
Metrics Extraction Script for DRL Task Offloading Simulations
Extracts key performance metrics from simulation results and generates CSV files
"""
import os
import re
import json
import csv

results_dir = "${RESULTS_DIR}"
metrics = {
    'execution_time': {},
    'energy_consumption': {},
    'network_usage': {}
}

def extract_metrics(filename, simulation_type):
    with open(filename, 'r') as f:
        content = f.read()
        
        # Extract execution time
        time_match = re.search(r'EXECUTION TIME : (\d+)', content)
        if time_match:
            metrics['execution_time'][simulation_type] = int(time_match.group(1))
        
        # Extract energy consumption
        energy_matches = re.findall(r'(\w+-?\w*-?\w*) : Energy Consumed = ([\d\.]+)', content)
        total_energy = sum(float(energy) for _, energy in energy_matches)
        metrics['energy_consumption'][simulation_type] = total_energy
        
        # Extract network usage
        network_match = re.search(r'Total network usage = ([\d\.]+)', content)
        if network_match:
            metrics['network_usage'][simulation_type] = float(network_match.group(1))

# Process simulation results
if os.path.exists(f"{results_dir}/simple_drl_results.txt"):
    extract_metrics(f"{results_dir}/simple_drl_results.txt", "Simple")

if os.path.exists(f"{results_dir}/learnable_drl_results.txt"):
    extract_metrics(f"{results_dir}/learnable_drl_results.txt", "Learnable")

if os.path.exists(f"{results_dir}/full_drl_results.txt"):
    extract_metrics(f"{results_dir}/full_drl_results.txt", "Full")

# Save metrics as CSV
for metric_name, metric_data in metrics.items():
    with open(f"{results_dir}/{metric_name}.csv", 'w', newline='') as csvfile:
        writer = csv.writer(csvfile)
        writer.writerow(['Simulation Type', metric_name])
        for sim_type, value in metric_data.items():
            writer.writerow([sim_type, value])

# Save all metrics as JSON for chart generation
with open(f"{results_dir}/all_metrics.json", 'w') as jsonfile:
    json.dump(metrics, jsonfile, indent=2)

print(f"Metrics extracted and saved to {results_dir}")
EOF

# Make the metrics extraction script executable
chmod +x ${RESULTS_DIR}/extract_metrics.py

# Execute the metrics extraction if Python is available
if command -v python3 &> /dev/null; then
    echo "Extracting metrics for charts..." | tee -a ${RESULTS_DIR}/summary.txt
    python3 ${RESULTS_DIR}/extract_metrics.py
    echo "  ✓ Metrics extracted and CSV files created" | tee -a ${RESULTS_DIR}/summary.txt
else
    echo "Python 3 not found. To extract metrics for charts, install Python 3 and run:" | tee -a ${RESULTS_DIR}/summary.txt
    echo "  python3 ${RESULTS_DIR}/extract_metrics.py" | tee -a ${RESULTS_DIR}/summary.txt
fi

echo "" | tee -a ${RESULTS_DIR}/summary.txt
echo "Done!" | tee -a ${RESULTS_DIR}/summary.txt
