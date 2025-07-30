package org.fog.drl;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents the state of the Mobile Edge Computing environment
 * This class captures device resources, network conditions, and task requirements
 * for the Deep Reinforcement Learning algorithm
 */
public class EnvironmentState {
    
    // Device state: map of device ID to resource utilization
    private Map<Integer, DeviceState> deviceStates;
    
    // Network state: map of (sourceID, destID) to latency
    private Map<String, Double> networkStates;
    
    // Task state: map of task name to requirements
    private Map<String, TaskState> taskStates;
    
    /**
     * Constructor
     */
    public EnvironmentState() {
        deviceStates = new HashMap<>();
        networkStates = new HashMap<>();
        taskStates = new HashMap<>();
    }
    
    /**
     * Add device state information
     * @param deviceId Device ID
     * @param cpuUtilization CPU utilization percentage
     * @param memoryUtilization Memory utilization percentage
     */
    public void addDeviceState(int deviceId, double cpuUtilization, double memoryUtilization) {
        deviceStates.put(deviceId, new DeviceState(cpuUtilization, memoryUtilization));
    }
    
    /**
     * Add network state information
     * @param sourceId Source device ID
     * @param destId Destination device ID
     * @param latency Network latency in milliseconds
     */
    public void addNetworkState(int sourceId, int destId, double latency) {
        String linkKey = sourceId + "-" + destId;
        networkStates.put(linkKey, latency);
    }
    
    /**
     * Add task state information
     * @param taskName Task/module name
     * @param cpuRequirement CPU requirement in MIPS
     * @param memoryRequirement Memory requirement in MB
     */
    public void addTaskState(String taskName, double cpuRequirement, double memoryRequirement) {
        taskStates.put(taskName, new TaskState(cpuRequirement, memoryRequirement));
    }
    
    /**
     * Convert the environment state to a feature vector for a specific task
     * @param taskName The task/module name
     * @return Feature vector representing the state
     */
    public double[] toFeatureVector(String taskName) {
        // Calculate the size of the feature vector
        int deviceStateSize = deviceStates.size() * 2; // CPU and memory utilization for each device
        int networkStateSize = networkStates.size();   // Latency for each network link
        int taskStateSize = 2;                         // CPU and memory requirements for the specific task
        
        int featureVectorSize = deviceStateSize + networkStateSize + taskStateSize;
        double[] featureVector = new double[featureVectorSize];
        
        int index = 0;
        
        // Add device state features
        for (Map.Entry<Integer, DeviceState> entry : deviceStates.entrySet()) {
            featureVector[index++] = normalizeValue(entry.getValue().getCpuUtilization(), 0, 100);
            featureVector[index++] = normalizeValue(entry.getValue().getMemoryUtilization(), 0, 100);
        }
        
        // Add network state features
        for (Map.Entry<String, Double> entry : networkStates.entrySet()) {
            featureVector[index++] = normalizeValue(entry.getValue(), 0, 500); // Assuming max latency of 500ms
        }
        
        // Add task state features
        if (taskStates.containsKey(taskName)) {
            TaskState taskState = taskStates.get(taskName);
            featureVector[index++] = normalizeValue(taskState.getCpuRequirement(), 0, 10000); // Assuming max CPU requirement of 10000 MIPS
            featureVector[index++] = normalizeValue(taskState.getMemoryRequirement(), 0, 10000); // Assuming max memory requirement of 10000 MB
        } else {
            // Default values if task not found
            featureVector[index++] = 0;
            featureVector[index++] = 0;
        }
        
        return featureVector;
    }
    
    /**
     * Normalize a value to the range [0, 1]
     * @param value Value to normalize
     * @param min Minimum possible value
     * @param max Maximum possible value
     * @return Normalized value
     */
    private double normalizeValue(double value, double min, double max) {
        return (value - min) / (max - min);
    }
    
    /**
     * Inner class representing device state
     */
    private class DeviceState {
        private double cpuUtilization;
        private double memoryUtilization;
        
        public DeviceState(double cpuUtilization, double memoryUtilization) {
            this.cpuUtilization = cpuUtilization;
            this.memoryUtilization = memoryUtilization;
        }
        
        public double getCpuUtilization() {
            return cpuUtilization;
        }
        
        public double getMemoryUtilization() {
            return memoryUtilization;
        }
    }
    
    /**
     * Inner class representing task state
     */
    private class TaskState {
        private double cpuRequirement;
        private double memoryRequirement;
        
        public TaskState(double cpuRequirement, double memoryRequirement) {
            this.cpuRequirement = cpuRequirement;
            this.memoryRequirement = memoryRequirement;
        }
        
        public double getCpuRequirement() {
            return cpuRequirement;
        }
        
        public double getMemoryRequirement() {
            return memoryRequirement;
        }
    }
}
