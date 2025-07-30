#!/usr/bin/env python3
"""
Metrics Extraction Script for DRL Task Offloading Simulations
Extracts key performance metrics from simulation results and generates CSV files
"""
import os
import re
import json
import csv

results_dir = "./results/data/20250730_225303"
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
