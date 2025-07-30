package org.fog.drl;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.entities.FogDevice;
import org.fog.placement.ModulePlacement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DRL-based Task Offloading Policy
 * This policy uses Deep Reinforcement Learning to make task offloading decisions
 * based on the approach described in "Deep Reinforcement Learning for Task Offloading in Mobile Edge Computing" (Wang et al.)
 */
public class DRLTaskOffloadingPolicy extends ModulePlacement {

    private DRLAgent agent;
    private EnvironmentState currentState;
    private TaskOffloadingMetrics metrics;
    
    // Cache for device selection to avoid frequent recomputation
    private Map<String, Integer> moduleToDeviceMapping;
    
    // Performance tracking
    private double cumulativeReward;
    private int decisionCounter;
    
    /**
     * Constructor
     * @param fogDevices List of fog devices in the system
     */
    public DRLTaskOffloadingPolicy(List<FogDevice> fogDevices) {
        super(fogDevices);
        this.moduleToDeviceMapping = new HashMap<>();
        this.metrics = new TaskOffloadingMetrics();
        this.cumulativeReward = 0.0;
        this.decisionCounter = 0;
    }
    
    @Override
    public void init() {
        // Initialize the DRL agent with the application and devices
        agent = new DRLAgent(getDeviceList(), getApplication());
        currentState = new EnvironmentState();
        
        // Initialize state with the current system state
        updateEnvironmentState();
        
        System.out.println("DRL Task Offloading Policy initialized at time " + CloudSim.clock());
    }
    
    @Override
    protected Map<String, Map<String, Integer>> getModuleToDeviceMapping() {
        // Create a mapping of modules to devices based on DRL decisions
        Map<String, Map<String, Integer>> mapping = new HashMap<>();
        
        // Update environment state
        updateEnvironmentState();
        
        // Get offloading decisions from DRL agent
        Map<String, Integer> decisions = agent.makeOffloadingDecisions(currentState);
        
        // Update the module to device mapping
        moduleToDeviceMapping.putAll(decisions);
        
        // Structure the mapping as expected by the iFogSim framework
        for (String deviceName : getDeviceNameToIdList().keySet()) {
            Map<String, Integer> deviceMapping = new HashMap<>();
            
            for (Map.Entry<String, Integer> entry : moduleToDeviceMapping.entrySet()) {
                String moduleName = entry.getKey();
                int deviceId = entry.getValue();
                
                if (deviceId == getDeviceNameToIdList().get(deviceName)) {
                    AppModule module = getApplication().getModuleByName(moduleName);
                    if (module != null) {
                        deviceMapping.put(moduleName, 1); // Number of instances of this module
                    }
                }
            }
            
            if (!deviceMapping.isEmpty()) {
                mapping.put(deviceName, deviceMapping);
            }
        }
        
        // Track this decision and update metrics
        decisionCounter++;
        double reward = evaluateDecision(decisions);
        cumulativeReward += reward;
        
        // Update the DRL agent with the reward
        agent.updateModel(reward, currentState);
        
        return mapping;
    }
    
    /**
     * Update the environment state with current system information
     */
    private void updateEnvironmentState() {
        currentState = new EnvironmentState();
        
        // Add device state information
        for (FogDevice device : getDeviceList()) {
            double cpuUtilization = device.getHost().getUtilizationOfCpu();
            double memoryUtilization = device.getHost().getRam() > 0 ? 
                    (device.getHost().getRamProvisioner().getUsedRam() * 100.0 / device.getHost().getRam()) : 0;
            
            currentState.addDeviceState(device.getId(), cpuUtilization, memoryUtilization);
            
            // Update metrics
            metrics.recordDeviceCpuUtilization(device.getName(), cpuUtilization);
            metrics.recordDeviceMemoryUtilization(device.getName(), memoryUtilization);
        }
        
        // Add network state information
        for (FogDevice device : getDeviceList()) {
            if (device.getParentId() != -1) {
                currentState.addNetworkState(device.getId(), device.getParentId(), device.getUplinkLatency());
            }
        }
        
        // Add task state information
        for (String moduleName : getApplication().getModules()) {
            AppModule module = getApplication().getModuleByName(moduleName);
            currentState.addTaskState(moduleName, module.getMips(), module.getRam());
        }
    }
    
    /**
     * Evaluate the quality of offloading decisions
     * @param decisions Offloading decisions made by the DRL agent
     * @return Calculated reward value
     */
    private double evaluateDecision(Map<String, Integer> decisions) {
        double latencyScore = evaluateLatency(decisions);
        double energyScore = evaluateEnergy(decisions);
        double resourceScore = evaluateResourceUtilization(decisions);
        
        // Weighted sum of scores (these weights can be tuned)
        double latencyWeight = 0.5;
        double energyWeight = 0.3;
        double resourceWeight = 0.2;
        
        double reward = latencyWeight * latencyScore + 
                        energyWeight * energyScore + 
                        resourceWeight * resourceScore;
        
        // Scale reward to a reasonable range
        reward = Math.max(-1.0, Math.min(1.0, reward));
        
        System.out.println("DRL decision evaluation - Reward: " + reward + 
                          " (Latency: " + latencyScore + 
                          ", Energy: " + energyScore + 
                          ", Resource: " + resourceScore + ")");
        
        return reward;
    }
    
    /**
     * Evaluate latency impact of offloading decisions
     * @param decisions Offloading decisions
     * @return Latency score (-1 to 1, higher is better)
     */
    private double evaluateLatency(Map<String, Integer> decisions) {
        double totalLatency = 0.0;
        
        for (Map.Entry<String, Integer> entry : decisions.entrySet()) {
            String moduleName = entry.getKey();
            int deviceId = entry.getValue();
            
            // Find the device
            FogDevice targetDevice = null;
            for (FogDevice device : getDeviceList()) {
                if (device.getId() == deviceId) {
                    targetDevice = device;
                    break;
                }
            }
            
            if (targetDevice != null) {
                // Calculate execution latency
                AppModule module = getApplication().getModuleByName(moduleName);
                double mips = module.getMips();
                double deviceSpeed = targetDevice.getHost().getTotalMips();
                double executionTime = mips > 0 && deviceSpeed > 0 ? mips / deviceSpeed * 1000 : 0; // in ms
                
                // Calculate transmission latency if not executed locally
                double transmissionTime = 0.0;
                if (targetDevice.getLevel() > 0) { // Not executed on cloud
                    // Simplified calculation based on network latency
                    transmissionTime = targetDevice.getUplinkLatency() * 2; // Round-trip time
                }
                
                double totalTime = executionTime + transmissionTime;
                totalLatency += totalTime;
                
                // Update metrics
                metrics.recordTaskExecutionTime(moduleName, executionTime);
                metrics.recordTaskTransmissionTime(moduleName, transmissionTime);
            }
        }
        
        // Convert to score: lower latency = higher score
        double normalizedLatency = Math.min(totalLatency / 1000.0, 1.0); // Cap at 1 second
        return 1.0 - (2.0 * normalizedLatency); // Map to [-1, 1]
    }
    
    /**
     * Evaluate energy impact of offloading decisions
     * @param decisions Offloading decisions
     * @return Energy score (-1 to 1, higher is better)
     */
    private double evaluateEnergy(Map<String, Integer> decisions) {
        Map<Integer, Double> deviceEnergy = new HashMap<>();
        
        for (Map.Entry<String, Integer> entry : decisions.entrySet()) {
            String moduleName = entry.getKey();
            int deviceId = entry.getValue();
            
            // Find the device
            FogDevice targetDevice = null;
            for (FogDevice device : getDeviceList()) {
                if (device.getId() == deviceId) {
                    targetDevice = device;
                    break;
                }
            }
            
            if (targetDevice != null) {
                // Calculate energy consumption
                AppModule module = getApplication().getModuleByName(moduleName);
                double mips = module.getMips();
                double energyPerMips = targetDevice.getEnergyConsumption(); // Energy per MIPS (simplified)
                
                double energy = mips * energyPerMips;
                
                // Add to device's total energy
                deviceEnergy.put(deviceId, deviceEnergy.getOrDefault(deviceId, 0.0) + energy);
            }
        }
        
        // Calculate total energy
        double totalEnergy = deviceEnergy.values().stream().mapToDouble(Double::doubleValue).sum();
        
        // Update metrics
        for (Map.Entry<Integer, Double> entry : deviceEnergy.entrySet()) {
            int deviceId = entry.getKey();
            double energy = entry.getValue();
            
            for (FogDevice device : getDeviceList()) {
                if (device.getId() == deviceId) {
                    metrics.recordDeviceEnergyConsumption(device.getName(), energy);
                    break;
                }
            }
        }
        
        // Convert to score: lower energy = higher score
        double normalizedEnergy = Math.min(totalEnergy / 100.0, 1.0); // Cap at 100 units
        return 1.0 - (2.0 * normalizedEnergy); // Map to [-1, 1]
    }
    
    /**
     * Evaluate resource utilization balance of offloading decisions
     * @param decisions Offloading decisions
     * @return Resource utilization score (-1 to 1, higher is better)
     */
    private double evaluateResourceUtilization(Map<String, Integer> decisions) {
        // Calculate CPU and memory load for each device after decisions
        Map<Integer, Double> cpuLoad = new HashMap<>();
        Map<Integer, Double> memoryLoad = new HashMap<>();
        
        // Initialize with current load
        for (FogDevice device : getDeviceList()) {
            cpuLoad.put(device.getId(), device.getHost().getUtilizationOfCpu());
            memoryLoad.put(device.getId(), device.getHost().getRam() > 0 ? 
                    device.getHost().getRamProvisioner().getUsedRam() / device.getHost().getRam() : 0);
        }
        
        // Add load from new decisions
        for (Map.Entry<String, Integer> entry : decisions.entrySet()) {
            String moduleName = entry.getKey();
            int deviceId = entry.getValue();
            
            AppModule module = getApplication().getModuleByName(moduleName);
            double mips = module.getMips();
            double ram = module.getRam();
            
            // Find device capacity
            for (FogDevice device : getDeviceList()) {
                if (device.getId() == deviceId) {
                    double deviceMips = device.getHost().getTotalMips();
                    double deviceRam = device.getHost().getRam();
                    
                    // Update loads
                    double newCpuLoad = deviceMips > 0 ? cpuLoad.get(deviceId) + (mips / deviceMips) : cpuLoad.get(deviceId);
                    double newMemoryLoad = deviceRam > 0 ? memoryLoad.get(deviceId) + (ram / deviceRam) : memoryLoad.get(deviceId);
                    
                    cpuLoad.put(deviceId, newCpuLoad);
                    memoryLoad.put(deviceId, newMemoryLoad);
                    break;
                }
            }
        }
        
        // Calculate standard deviation as a measure of balance (lower is better)
        double avgCpuLoad = cpuLoad.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double avgMemoryLoad = memoryLoad.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        
        double cpuStdDev = 0.0;
        double memStdDev = 0.0;
        
        for (Integer deviceId : cpuLoad.keySet()) {
            cpuStdDev += Math.pow(cpuLoad.get(deviceId) - avgCpuLoad, 2);
            memStdDev += Math.pow(memoryLoad.get(deviceId) - avgMemoryLoad, 2);
        }
        
        cpuStdDev = Math.sqrt(cpuStdDev / cpuLoad.size());
        memStdDev = Math.sqrt(memStdDev / memoryLoad.size());
        
        // Calculate combined standard deviation
        double combinedStdDev = (cpuStdDev + memStdDev) / 2.0;
        
        // Convert to score: lower deviation = higher score (better balance)
        double normalizedStdDev = Math.min(combinedStdDev, 0.5) / 0.5; // Cap at 0.5
        return 1.0 - (2.0 * normalizedStdDev); // Map to [-1, 1]
    }
    
    /**
     * Get the performance metrics
     * @return Task offloading metrics
     */
    public TaskOffloadingMetrics getMetrics() {
        return metrics;
    }
    
    /**
     * Get the average reward
     * @return Average reward across all decisions
     */
    public double getAverageReward() {
        return decisionCounter > 0 ? cumulativeReward / decisionCounter : 0.0;
    }
    
    /**
     * Generate performance report
     * @return Performance report string
     */
    public String generatePerformanceReport() {
        StringBuilder report = new StringBuilder();
        report.append("DRL Task Offloading Performance Report\n");
        report.append("======================================\n\n");
        
        report.append("DRL Agent Performance:\n");
        report.append("  Total Decisions: ").append(decisionCounter).append("\n");
        report.append("  Cumulative Reward: ").append(String.format("%.2f", cumulativeReward)).append("\n");
        report.append("  Average Reward: ").append(String.format("%.2f", getAverageReward())).append("\n\n");
        
        report.append(metrics.generateSummaryReport());
        
        return report.toString();
    }
}
