package org.fog.drl;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.text.DecimalFormat;
import java.util.ArrayList;

/**
 * Class for visualizing and exporting results from the DRL task offloading simulations
 * Generates CSV files that can be used with external tools for visualization
 */
public class ResultsVisualizer {

    // Constants for file naming
    private static final String RESULTS_DIRECTORY = "simulation_results/";
    private static final String LATENCY_FILE = "latency_results.csv";
    private static final String ENERGY_FILE = "energy_results.csv";
    private static final String RESOURCE_UTILIZATION_FILE = "resource_utilization.csv";
    private static final String DRL_TRAINING_FILE = "drl_training.csv";
    
    // Decimal formatter for consistent output
    private static final DecimalFormat df = new DecimalFormat("#.###");
    
    /**
     * Save latency comparison results to CSV
     * @param approachToLatency Map of approach names to latency values
     * @param description Optional description for the file header
     */
    public static void saveLatencyResults(Map<String, Double> approachToLatency, String description) {
        try {
            // Create directory if it doesn't exist
            createDirectory();
            
            // Create CSV writer
            BufferedWriter writer = new BufferedWriter(new FileWriter(RESULTS_DIRECTORY + LATENCY_FILE));
            
            // Write header with description
            if (description != null && !description.isEmpty()) {
                writer.write("# " + description);
                writer.newLine();
            }
            writer.write("Approach,Latency (ms)");
            writer.newLine();
            
            // Write data
            for (Map.Entry<String, Double> entry : approachToLatency.entrySet()) {
                writer.write(entry.getKey() + "," + df.format(entry.getValue()));
                writer.newLine();
            }
            
            // Close writer
            writer.close();
            
            System.out.println("Latency results saved to " + RESULTS_DIRECTORY + LATENCY_FILE);
            
        } catch (IOException e) {
            System.err.println("Error saving latency results: " + e.getMessage());
        }
    }
    
    /**
     * Save energy consumption comparison results to CSV
     * @param approachToEnergy Map of approach names to energy values
     * @param description Optional description for the file header
     */
    public static void saveEnergyResults(Map<String, Double> approachToEnergy, String description) {
        try {
            // Create directory if it doesn't exist
            createDirectory();
            
            // Create CSV writer
            BufferedWriter writer = new BufferedWriter(new FileWriter(RESULTS_DIRECTORY + ENERGY_FILE));
            
            // Write header with description
            if (description != null && !description.isEmpty()) {
                writer.write("# " + description);
                writer.newLine();
            }
            writer.write("Approach,Energy (J)");
            writer.newLine();
            
            // Write data
            for (Map.Entry<String, Double> entry : approachToEnergy.entrySet()) {
                writer.write(entry.getKey() + "," + df.format(entry.getValue()));
                writer.newLine();
            }
            
            // Close writer
            writer.close();
            
            System.out.println("Energy results saved to " + RESULTS_DIRECTORY + ENERGY_FILE);
            
        } catch (IOException e) {
            System.err.println("Error saving energy results: " + e.getMessage());
        }
    }
    
    /**
     * Save resource utilization data to CSV
     * @param deviceToUtilization Map of device names to their utilization over time
     * @param timePoints List of time points
     * @param description Optional description for the file header
     */
    public static void saveResourceUtilization(Map<String, List<Double>> deviceToUtilization, 
                                               List<Double> timePoints, String description) {
        try {
            // Create directory if it doesn't exist
            createDirectory();
            
            // Create CSV writer
            BufferedWriter writer = new BufferedWriter(new FileWriter(RESULTS_DIRECTORY + RESOURCE_UTILIZATION_FILE));
            
            // Write header with description
            if (description != null && !description.isEmpty()) {
                writer.write("# " + description);
                writer.newLine();
            }
            
            // Write header with device names
            writer.write("Time");
            for (String deviceName : deviceToUtilization.keySet()) {
                writer.write("," + deviceName);
            }
            writer.newLine();
            
            // Write data for each time point
            for (int i = 0; i < timePoints.size(); i++) {
                writer.write(df.format(timePoints.get(i)));
                
                for (List<Double> utilization : deviceToUtilization.values()) {
                    if (i < utilization.size()) {
                        writer.write("," + df.format(utilization.get(i)));
                    } else {
                        writer.write(",0");
                    }
                }
                
                writer.newLine();
            }
            
            // Close writer
            writer.close();
            
            System.out.println("Resource utilization data saved to " + RESULTS_DIRECTORY + RESOURCE_UTILIZATION_FILE);
            
        } catch (IOException e) {
            System.err.println("Error saving resource utilization data: " + e.getMessage());
        }
    }
    
    /**
     * Save DRL training progress data to CSV
     * @param episodes List of training episodes
     * @param rewards List of rewards per episode
     * @param epsilons List of epsilon values per episode
     * @param losses List of loss values per episode
     * @param description Optional description for the file header
     */
    public static void saveDRLTrainingProgress(List<Integer> episodes, List<Double> rewards,
                                              List<Double> epsilons, List<Double> losses, String description) {
        try {
            // Create directory if it doesn't exist
            createDirectory();
            
            // Create CSV writer
            BufferedWriter writer = new BufferedWriter(new FileWriter(RESULTS_DIRECTORY + DRL_TRAINING_FILE));
            
            // Write header with description
            if (description != null && !description.isEmpty()) {
                writer.write("# " + description);
                writer.newLine();
            }
            writer.write("Episode,Reward,Epsilon,Loss");
            writer.newLine();
            
            // Write data for each episode
            for (int i = 0; i < episodes.size(); i++) {
                writer.write(episodes.get(i) + "," + 
                            df.format(rewards.get(i)) + "," + 
                            df.format(epsilons.get(i)) + "," + 
                            df.format(losses.get(i)));
                writer.newLine();
            }
            
            // Close writer
            writer.close();
            
            System.out.println("DRL training progress saved to " + RESULTS_DIRECTORY + DRL_TRAINING_FILE);
            
        } catch (IOException e) {
            System.err.println("Error saving DRL training progress: " + e.getMessage());
        }
    }
    
    /**
     * Save custom metrics to a CSV file
     * @param filePath File path relative to results directory
     * @param headers CSV header names
     * @param data List of data rows (each row must be a string array matching headers length)
     * @param description Optional description for the file header
     */
    public static void saveCustomData(String filePath, String[] headers, List<String[]> data, String description) {
        try {
            // Create directory if it doesn't exist
            createDirectory();
            
            // Create CSV writer
            BufferedWriter writer = new BufferedWriter(new FileWriter(RESULTS_DIRECTORY + filePath));
            
            // Write header with description
            if (description != null && !description.isEmpty()) {
                writer.write("# " + description);
                writer.newLine();
            }
            
            // Write headers
            for (int i = 0; i < headers.length; i++) {
                writer.write(headers[i]);
                if (i < headers.length - 1) {
                    writer.write(",");
                }
            }
            writer.newLine();
            
            // Write data
            for (String[] row : data) {
                for (int i = 0; i < row.length; i++) {
                    writer.write(row[i]);
                    if (i < row.length - 1) {
                        writer.write(",");
                    }
                }
                writer.newLine();
            }
            
            // Close writer
            writer.close();
            
            System.out.println("Custom data saved to " + RESULTS_DIRECTORY + filePath);
            
        } catch (IOException e) {
            System.err.println("Error saving custom data: " + e.getMessage());
        }
    }
    
    /**
     * Generate parameter sweep data for simulation
     * @param paramName Parameter name
     * @param paramValues Parameter values
     * @param latencyValues Corresponding latency values
     * @param energyValues Corresponding energy values
     * @param description Optional description for the file header
     */
    public static void saveParameterSweepResults(String paramName, List<Double> paramValues,
                                                List<Double> latencyValues, List<Double> energyValues, String description) {
        try {
            // Create directory if it doesn't exist
            createDirectory();
            
            // Create filename based on parameter
            String filename = paramName.toLowerCase().replace(" ", "_") + "_sweep.csv";
            
            // Create CSV writer
            BufferedWriter writer = new BufferedWriter(new FileWriter(RESULTS_DIRECTORY + filename));
            
            // Write header with description
            if (description != null && !description.isEmpty()) {
                writer.write("# " + description);
                writer.newLine();
            }
            writer.write(paramName + ",Latency (ms),Energy (J)");
            writer.newLine();
            
            // Write data
            for (int i = 0; i < paramValues.size(); i++) {
                writer.write(df.format(paramValues.get(i)) + "," + 
                            df.format(latencyValues.get(i)) + "," + 
                            df.format(energyValues.get(i)));
                writer.newLine();
            }
            
            // Close writer
            writer.close();
            
            System.out.println("Parameter sweep results saved to " + RESULTS_DIRECTORY + filename);
            
        } catch (IOException e) {
            System.err.println("Error saving parameter sweep results: " + e.getMessage());
        }
    }
    
    /**
     * Generate summary statistics for the simulation results
     * @param metrics TaskOffloadingMetrics instance with collected metrics
     * @return Summary statistics as a string
     */
    public static String generateSummaryStatistics(TaskOffloadingMetrics metrics) {
        StringBuilder summary = new StringBuilder();
        
        summary.append("SIMULATION SUMMARY STATISTICS\n");
        summary.append("============================\n\n");
        
        // Add application metrics
        summary.append("Application Performance:\n");
        summary.append("  Average Latency: ").append(df.format(metrics.getAverageLatency())).append(" ms\n");
        summary.append("  Average Energy Consumption: ").append(df.format(metrics.getAverageEnergy())).append(" J\n");
        summary.append("  Total Tasks Processed: ").append(metrics.getTotalTasksProcessed()).append("\n");
        summary.append("  Tasks Completed Successfully: ").append(metrics.getSuccessfulTasks()).append("\n");
        summary.append("  Tasks Failed: ").append(metrics.getFailedTasks()).append("\n");
        summary.append("  Success Rate: ").append(df.format(metrics.getSuccessRate() * 100)).append("%\n\n");
        
        // Add resource utilization metrics
        summary.append("Resource Utilization:\n");
        summary.append("  Average CPU Utilization: ").append(df.format(metrics.getAverageCpuUtilization() * 100)).append("%\n");
        summary.append("  Average RAM Utilization: ").append(df.format(metrics.getAverageMemoryUtilization() * 100)).append("%\n");
        summary.append("  Total Data Transferred: ").append(df.format(metrics.getTotalDataTransferred())).append(" KB\n\n");
        
        // Add DRL metrics
        summary.append("DRL Performance:\n");
        summary.append("  Offloading Decision Changes: ").append(metrics.getOffloadingDecisionChanges()).append("\n");
        summary.append("  Average Reward: ").append(df.format(metrics.getAverageReward())).append("\n");
        summary.append("  Training Episodes: ").append(metrics.getTrainingEpisodes()).append("\n");
        summary.append("  Final Epsilon Value: ").append(df.format(metrics.getFinalEpsilon())).append("\n");
        
        return summary.toString();
    }
    
    /**
     * Creates the results directory if it doesn't exist
     */
    private static void createDirectory() {
        java.io.File directory = new java.io.File(RESULTS_DIRECTORY);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }
    
    /**
     * Main method with an example of using the visualizer
     * @param args Command-line arguments
     */
    public static void main(String[] args) {
        // Example usage
        System.out.println("ResultsVisualizer Example");
        System.out.println("=======================");
        
        // Example latency data
        Map<String, Double> latencyMap = new HashMap<>();
        latencyMap.put("DRL", 45.3);
        latencyMap.put("CloudOnly", 142.7);
        latencyMap.put("EdgeOnly", 68.2);
        latencyMap.put("MobileOnly", 38.9);
        saveLatencyResults(latencyMap, "Latency Comparison between Task Offloading Strategies");
        
        // Example energy data
        Map<String, Double> energyMap = new HashMap<>();
        energyMap.put("DRL", 125.8);
        energyMap.put("CloudOnly", 237.4);
        energyMap.put("EdgeOnly", 158.3);
        energyMap.put("MobileOnly", 95.7);
        saveEnergyResults(energyMap, "Energy Consumption Comparison between Task Offloading Strategies");
        
        // Example resource utilization data
        Map<String, List<Double>> utilizationMap = new HashMap<>();
        List<Double> cloudUtil = new ArrayList<>();
        List<Double> edgeUtil = new ArrayList<>();
        List<Double> mobileUtil = new ArrayList<>();
        List<Double> timePoints = new ArrayList<>();
        
        for (int i = 0; i < 10; i++) {
            timePoints.add((double)i * 10);
            cloudUtil.add(0.2 + Math.random() * 0.3);
            edgeUtil.add(0.4 + Math.random() * 0.4);
            mobileUtil.add(0.1 + Math.random() * 0.6);
        }
        
        utilizationMap.put("Cloud", cloudUtil);
        utilizationMap.put("Edge", edgeUtil);
        utilizationMap.put("Mobile", mobileUtil);
        
        saveResourceUtilization(utilizationMap, timePoints, "Resource Utilization over Time");
        
        // Example DRL training progress
        List<Integer> episodes = new ArrayList<>();
        List<Double> rewards = new ArrayList<>();
        List<Double> epsilons = new ArrayList<>();
        List<Double> losses = new ArrayList<>();
        
        for (int i = 0; i < 100; i++) {
            episodes.add(i);
            rewards.add(-10.0 + 15 * (1 - Math.exp(-i/20.0)) + Math.random() * 3 - 1.5);
            epsilons.add(1.0 - (i / 100.0));
            losses.add(5.0 * Math.exp(-i/20.0) + Math.random() * 1.2);
        }
        
        saveDRLTrainingProgress(episodes, rewards, epsilons, losses, "DRL Agent Training Progress");
        
        System.out.println("Example visualization data generated.");
    }
}
