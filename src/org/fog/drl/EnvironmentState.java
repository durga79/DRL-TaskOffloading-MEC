package org.fog.drl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.fog.entities.FogDevice;
import org.fog.application.AppModule;

/**
 * Represents the state of the MEC environment for DRL decision-making
 */
public class EnvironmentState {
    // Resource utilization for fog devices
    private Map<String, Double> cpuUtilization;
    private Map<String, Double> ramUtilization;
    private Map<String, Double> bwUtilization;
    
    // Network conditions between devices
    private Map<String, Map<String, Double>> networkLatencies;
    
    // Task characteristics
    private Map<String, Double> taskMips;
    private Map<String, Double> taskRam;
    private Map<String, Double> taskDataSize;
    
    // Mobile device battery levels
    private Map<String, Double> deviceBatteryLevels;
    
    // Current task to be offloaded
    private String currentTaskId;
    private String currentTaskType;

    public EnvironmentState() {
        cpuUtilization = new HashMap<>();
        ramUtilization = new HashMap<>();
        bwUtilization = new HashMap<>();
        networkLatencies = new HashMap<>();
        taskMips = new HashMap<>();
        taskRam = new HashMap<>();
        taskDataSize = new HashMap<>();
        deviceBatteryLevels = new HashMap<>();
    }
    
    /**
     * Updates the environment state based on current system conditions
     * @param fogDevices List of fog devices in the system
     * @param modules List of application modules
     */
    public void updateState(List<FogDevice> fogDevices, List<AppModule> modules) {
        // Update device utilization metrics
        for (FogDevice device : fogDevices) {
            String deviceName = device.getName();
            
            // Calculate resource utilization - simplified due to API limitations
            // Use available APIs to estimate utilization
            double totalMips = device.getHost().getTotalMips();
            // Simplified estimation - assume 50% utilization as we can't get allocated MIPS directly
            double cpuUtil = 0.5; 
            
            double totalRam = device.getHost().getRam();
            // Simplified estimation for RAM - assume 40% utilization
            double ramUtil = 0.4;
            
            double totalBw = device.getUplinkBandwidth();
            // Simplified estimation for BW - assume 30% utilization
            double bwUtil = 0.3;
            
            // Store utilization values
            cpuUtilization.put(deviceName, cpuUtil);
            ramUtilization.put(deviceName, ramUtil);
            bwUtilization.put(deviceName, bwUtil);
            
            // Simulate battery level for mobile devices (lower levels for devices with higher IDs)
            if (deviceName.startsWith("mobile-")) {
                String[] parts = deviceName.split("-");
                if (parts.length >= 3) {
                    int deviceId = Integer.parseInt(parts[2]);
                    double batteryLevel = 1.0 - (deviceId * 0.1); // Simple simulation of battery levels
                    deviceBatteryLevels.put(deviceName, Math.max(0.1, batteryLevel));
                }
            }
        }
        
        // Update network latencies between devices
        for (FogDevice source : fogDevices) {
            Map<String, Double> latencies = new HashMap<>();
            for (FogDevice destination : fogDevices) {
                if (source.getId() == destination.getId()) {
                    latencies.put(destination.getName(), 0.0); // No latency to self
                } else if (source.getParentId() == destination.getId() || 
                           destination.getParentId() == source.getId()) {
                    // Direct parent-child connection
                    latencies.put(destination.getName(), (double) source.getUplinkLatency());
                } else {
                    // Simplified network model - higher latency for distant nodes
                    int sourceLevel = source.getLevel();
                    int destLevel = destination.getLevel();
                    double latency = Math.abs(sourceLevel - destLevel) * 50.0;
                    latencies.put(destination.getName(), latency);
                }
            }
            networkLatencies.put(source.getName(), latencies);
        }
        
        // Update task characteristics
        for (AppModule module : modules) {
            String moduleId = module.getName();
            taskMips.put(moduleId, (double) module.getMips());
            taskRam.put(moduleId, (double) module.getRam());
            // Simulated data size based on RAM requirements
            taskDataSize.put(moduleId, module.getRam() * 0.5);
        }
    }

    /**
     * Converts the environment state to a feature vector for DRL input
     * @param devices List of device names to consider
     * @param currentModule Current module to be placed
     * @return Feature vector representing the state
     */
    public double[] toFeatureVector(List<String> devices, String currentModule) {
        this.currentTaskId = currentModule;
        
        // For each potential device, create features
        double[] features = new double[devices.size() * 5]; // 5 features per device
        
        for (int i = 0; i < devices.size(); i++) {
            String deviceName = devices.get(i);
            int baseIndex = i * 5;
            
            // CPU utilization
            features[baseIndex] = cpuUtilization.getOrDefault(deviceName, 0.0);
            
            // RAM utilization
            features[baseIndex + 1] = ramUtilization.getOrDefault(deviceName, 0.0);
            
            // BW utilization
            features[baseIndex + 2] = bwUtilization.getOrDefault(deviceName, 0.0);
            
            // Battery level (1.0 for cloud/edge, actual level for mobile)
            features[baseIndex + 3] = deviceBatteryLevels.getOrDefault(deviceName, 1.0);
            
            // Network latency from current device to the module's optimal location
            // Simplified - using average latency to other devices
            double avgLatency = 0.0;
            Map<String, Double> latencies = networkLatencies.getOrDefault(deviceName, new HashMap<>());
            for (Double latency : latencies.values()) {
                avgLatency += latency;
            }
            if (!latencies.isEmpty()) {
                avgLatency /= latencies.size();
            }
            features[baseIndex + 4] = avgLatency / 100.0; // Normalize by assuming max latency is 100ms
        }
        
        return features;
    }

    // Getters
    public Map<String, Double> getCpuUtilization() {
        return cpuUtilization;
    }

    public Map<String, Double> getRamUtilization() {
        return ramUtilization;
    }

    public Map<String, Double> getBwUtilization() {
        return bwUtilization;
    }

    public Map<String, Map<String, Double>> getNetworkLatencies() {
        return networkLatencies;
    }

    public Map<String, Double> getTaskMips() {
        return taskMips;
    }

    public Map<String, Double> getTaskRam() {
        return taskRam;
    }

    public Map<String, Double> getTaskDataSize() {
        return taskDataSize;
    }

    public String getCurrentTaskId() {
        return currentTaskId;
    }

    public void setCurrentTaskId(String currentTaskId) {
        this.currentTaskId = currentTaskId;
    }

    public String getCurrentTaskType() {
        return currentTaskType;
    }

    public void setCurrentTaskType(String currentTaskType) {
        this.currentTaskType = currentTaskType;
    }
}
