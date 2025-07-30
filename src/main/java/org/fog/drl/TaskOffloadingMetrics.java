package org.fog.drl;

import java.util.HashMap;
import java.util.Map;

/**
 * Metrics collector for task offloading system evaluation
 * Tracks performance metrics like latency, energy consumption, and resource utilization
 */
public class TaskOffloadingMetrics {
    
    // Latency metrics
    private Map<String, Double> taskExecutionTimes;    // Execution time for each task
    private Map<String, Double> taskTransmissionTimes; // Transmission time for each task
    private double totalLatency;                       // Total system latency
    private double averageLatency;                     // Average latency per task
    
    // Energy metrics
    private Map<String, Double> deviceEnergyConsumption; // Energy consumption per device
    private double totalEnergy;                          // Total energy consumption
    
    // Resource utilization metrics
    private Map<String, Double> deviceCpuUtilization;    // CPU utilization per device
    private Map<String, Double> deviceMemoryUtilization; // Memory utilization per device
    private double averageCpuUtilization;                // Average CPU utilization
    private double averageMemoryUtilization;             // Average memory utilization
    
    // Network metrics
    private double totalDataTransferred;                 // Total data transferred in network
    private double networkBandwidthUtilization;          // Network bandwidth utilization
    
    // Task completion metrics
    private int totalTasksProcessed;                     // Total number of tasks processed
    private int tasksCompletedSuccessfully;              // Tasks completed within deadline
    private int tasksTimedOut;                           // Tasks that timed out
    
    /**
     * Constructor
     */
    public TaskOffloadingMetrics() {
        taskExecutionTimes = new HashMap<>();
        taskTransmissionTimes = new HashMap<>();
        deviceEnergyConsumption = new HashMap<>();
        deviceCpuUtilization = new HashMap<>();
        deviceMemoryUtilization = new HashMap<>();
        totalLatency = 0.0;
        averageLatency = 0.0;
        totalEnergy = 0.0;
        averageCpuUtilization = 0.0;
        averageMemoryUtilization = 0.0;
        totalDataTransferred = 0.0;
        networkBandwidthUtilization = 0.0;
        totalTasksProcessed = 0;
        tasksCompletedSuccessfully = 0;
        tasksTimedOut = 0;
    }
    
    /**
     * Record task execution time
     * @param taskId Task identifier
     * @param executionTime Execution time in milliseconds
     */
    public void recordTaskExecutionTime(String taskId, double executionTime) {
        taskExecutionTimes.put(taskId, executionTime);
        updateLatencyMetrics();
    }
    
    /**
     * Record task transmission time
     * @param taskId Task identifier
     * @param transmissionTime Transmission time in milliseconds
     */
    public void recordTaskTransmissionTime(String taskId, double transmissionTime) {
        taskTransmissionTimes.put(taskId, transmissionTime);
        updateLatencyMetrics();
    }
    
    /**
     * Record device energy consumption
     * @param deviceId Device identifier
     * @param energyConsumption Energy consumption in joules
     */
    public void recordDeviceEnergyConsumption(String deviceId, double energyConsumption) {
        deviceEnergyConsumption.put(deviceId, energyConsumption);
        updateEnergyMetrics();
    }
    
    /**
     * Record device CPU utilization
     * @param deviceId Device identifier
     * @param utilization CPU utilization percentage (0-100)
     */
    public void recordDeviceCpuUtilization(String deviceId, double utilization) {
        deviceCpuUtilization.put(deviceId, utilization);
        updateResourceMetrics();
    }
    
    /**
     * Record device memory utilization
     * @param deviceId Device identifier
     * @param utilization Memory utilization percentage (0-100)
     */
    public void recordDeviceMemoryUtilization(String deviceId, double utilization) {
        deviceMemoryUtilization.put(deviceId, utilization);
        updateResourceMetrics();
    }
    
    /**
     * Record data transfer
     * @param dataSize Data size in bytes
     */
    public void recordDataTransfer(double dataSize) {
        totalDataTransferred += dataSize;
    }
    
    /**
     * Record network bandwidth utilization
     * @param utilization Bandwidth utilization percentage (0-100)
     */
    public void recordNetworkBandwidthUtilization(double utilization) {
        networkBandwidthUtilization = utilization;
    }
    
    /**
     * Record task completion
     * @param successful Whether the task completed successfully within deadline
     */
    public void recordTaskCompletion(boolean successful) {
        totalTasksProcessed++;
        if (successful) {
            tasksCompletedSuccessfully++;
        } else {
            tasksTimedOut++;
        }
    }
    
    /**
     * Update latency metrics
     */
    private void updateLatencyMetrics() {
        totalLatency = 0.0;
        
        // Calculate total latency as sum of execution and transmission times
        for (String taskId : taskExecutionTimes.keySet()) {
            double executionTime = taskExecutionTimes.get(taskId);
            double transmissionTime = taskTransmissionTimes.containsKey(taskId) ? 
                                     taskTransmissionTimes.get(taskId) : 0.0;
            
            totalLatency += (executionTime + transmissionTime);
        }
        
        // Calculate average latency
        if (!taskExecutionTimes.isEmpty()) {
            averageLatency = totalLatency / taskExecutionTimes.size();
        }
    }
    
    /**
     * Update energy metrics
     */
    private void updateEnergyMetrics() {
        totalEnergy = deviceEnergyConsumption.values().stream()
                                            .mapToDouble(Double::doubleValue)
                                            .sum();
    }
    
    /**
     * Update resource utilization metrics
     */
    private void updateResourceMetrics() {
        // Calculate average CPU utilization
        if (!deviceCpuUtilization.isEmpty()) {
            averageCpuUtilization = deviceCpuUtilization.values().stream()
                                                      .mapToDouble(Double::doubleValue)
                                                      .average()
                                                      .orElse(0.0);
        }
        
        // Calculate average memory utilization
        if (!deviceMemoryUtilization.isEmpty()) {
            averageMemoryUtilization = deviceMemoryUtilization.values().stream()
                                                            .mapToDouble(Double::doubleValue)
                                                            .average()
                                                            .orElse(0.0);
        }
    }
    
    /**
     * Get total latency
     * @return Total latency in milliseconds
     */
    public double getTotalLatency() {
        return totalLatency;
    }
    
    /**
     * Get average latency
     * @return Average latency in milliseconds
     */
    public double getAverageLatency() {
        return averageLatency;
    }
    
    /**
     * Get total energy consumption
     * @return Total energy consumption in joules
     */
    public double getTotalEnergy() {
        return totalEnergy;
    }
    
    /**
     * Get average CPU utilization
     * @return Average CPU utilization percentage
     */
    public double getAverageCpuUtilization() {
        return averageCpuUtilization;
    }
    
    /**
     * Get average memory utilization
     * @return Average memory utilization percentage
     */
    public double getAverageMemoryUtilization() {
        return averageMemoryUtilization;
    }
    
    /**
     * Get total data transferred
     * @return Total data transferred in bytes
     */
    public double getTotalDataTransferred() {
        return totalDataTransferred;
    }
    
    /**
     * Get network bandwidth utilization
     * @return Network bandwidth utilization percentage
     */
    public double getNetworkBandwidthUtilization() {
        return networkBandwidthUtilization;
    }
    
    /**
     * Get total tasks processed
     * @return Total number of tasks processed
     */
    public int getTotalTasksProcessed() {
        return totalTasksProcessed;
    }
    
    /**
     * Get tasks completed successfully rate
     * @return Percentage of tasks completed successfully
     */
    public double getTaskSuccessRate() {
        return totalTasksProcessed > 0 ? 
               (double) tasksCompletedSuccessfully / totalTasksProcessed * 100.0 : 0.0;
    }
    
    /**
     * Generate a performance summary report
     * @return Performance summary
     */
    public String generateSummaryReport() {
        StringBuilder report = new StringBuilder();
        report.append("Task Offloading Performance Report\n");
        report.append("=================================\n\n");
        
        report.append("Latency Metrics:\n");
        report.append("  Total Latency: ").append(String.format("%.2f", totalLatency)).append(" ms\n");
        report.append("  Average Latency: ").append(String.format("%.2f", averageLatency)).append(" ms\n\n");
        
        report.append("Energy Metrics:\n");
        report.append("  Total Energy Consumption: ").append(String.format("%.2f", totalEnergy)).append(" J\n\n");
        
        report.append("Resource Utilization:\n");
        report.append("  Average CPU Utilization: ").append(String.format("%.2f", averageCpuUtilization)).append("%\n");
        report.append("  Average Memory Utilization: ").append(String.format("%.2f", averageMemoryUtilization)).append("%\n\n");
        
        report.append("Network Metrics:\n");
        report.append("  Total Data Transferred: ").append(String.format("%.2f", totalDataTransferred / 1024)).append(" KB\n");
        report.append("  Bandwidth Utilization: ").append(String.format("%.2f", networkBandwidthUtilization)).append("%\n\n");
        
        report.append("Task Completion Metrics:\n");
        report.append("  Total Tasks Processed: ").append(totalTasksProcessed).append("\n");
        report.append("  Tasks Completed Successfully: ").append(tasksCompletedSuccessfully).append(" (");
        report.append(String.format("%.2f", getTaskSuccessRate())).append("%)\n");
        report.append("  Tasks Timed Out: ").append(tasksTimedOut).append(" (");
        report.append(String.format("%.2f", 100.0 - getTaskSuccessRate())).append("%)\n");
        
        return report.toString();
    }
}
