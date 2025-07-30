package org.fog.drl;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.entities.FogDevice;
import org.fog.entities.Tuple;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacement;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A greedy baseline task offloading policy that uses a simple heuristic approach
 * for comparison with the DRL-based task offloading policy
 */
public class GreedyTaskOffloadingPolicy extends ModulePlacement {
    
    // Map of module to device (offloading decision)
    protected Map<String, Integer> moduleToDeviceMap;
    private TaskOffloadingMetrics metrics;
    
    /**
     * Constructor for the greedy task offloading policy
     * @param fogDevices List of fog devices (cloud, edge, mobile)
     */
    public GreedyTaskOffloadingPolicy(List<FogDevice> fogDevices) {
        super(fogDevices);
        moduleToDeviceMap = new HashMap<>();
        metrics = new TaskOffloadingMetrics();
    }
    
    @Override
    protected void mapModules() {
        // Get all app modules
        for(String appId : getApplications().keySet()) {
            List<AppModule> modules = getApplications().get(appId).getModules();
            
            // For each module, find the best device using a greedy heuristic
            for(AppModule module : modules) {
                FogDevice bestDevice = findBestDeviceForModule(module);
                
                if(bestDevice != null) {
                    // Create module instance on the device
                    createModuleInstanceOnDevice(module, bestDevice);
                    moduleToDeviceMap.put(module.getName(), bestDevice.getId());
                    
                    // Log the offloading decision
                    System.out.println("[GreedyPolicy] Placed module " + module.getName() + " on device " + bestDevice.getName());
                }
            }
        }
    }
    
    /**
     * Find the best device for a module using a greedy heuristic
     * This prioritizes devices with:
     * 1. Sufficient resources (CPU, RAM)
     * 2. Lowest estimated latency for execution
     * 
     * @param module The application module to place
     * @return The best FogDevice for the module
     */
    private FogDevice findBestDeviceForModule(AppModule module) {
        FogDevice bestDevice = null;
        double bestScore = Double.MAX_VALUE; // Lower score is better
        
        // Get module resource requirements
        double cpuReq = module.getMips();
        double ramReq = module.getRam();
        
        // Evaluate each device
        for(FogDevice device : getFogDevices()) {
            // Check if device has sufficient resources
            double availableMips = device.getHost().getAvailableMips();
            double availableRam = device.getHost().getRam() - device.getHost().getUtilizationOfRam();
            
            if(availableMips >= cpuReq && availableRam >= ramReq) {
                // Calculate score based on device level (Cloud=0, Edge=1, Mobile=2)
                // Higher level generally means closer to the user but less powerful
                int level = device.getLevel();
                double latencyPenalty = 0;
                
                // Add estimated latency penalty based on level
                if(level == 0) { // Cloud
                    latencyPenalty = 100; // High latency penalty
                } else if(level == 1) { // Edge
                    latencyPenalty = 20;  // Medium latency penalty
                } else { // Mobile
                    latencyPenalty = 5;   // Low latency penalty
                }
                
                // Calculate resource strain (how much of available resources this module would use)
                double cpuStrain = cpuReq / availableMips;
                double ramStrain = ramReq / availableRam;
                double resourceStrain = (cpuStrain + ramStrain) / 2;
                
                // Calculate score (lower is better) - balance latency and resource strain
                double deviceScore = latencyPenalty + (resourceStrain * 50);
                
                // Update best device if this one has a better score
                if(deviceScore < bestScore) {
                    bestScore = deviceScore;
                    bestDevice = device;
                }
            }
        }
        
        return bestDevice;
    }
    
    @Override
    public void processEvent(int eventType, int srcModuleId, int dstModuleId, Tuple tuple) {
        // Record the start time for latency calculation
        double startTime = CloudSim.clock();
        
        // Process the tuple
        super.processEvent(eventType, srcModuleId, dstModuleId, tuple);
        
        // Record the end time
        double endTime = CloudSim.clock();
        double latency = endTime - startTime;
        
        // Update metrics
        metrics.recordLatency(latency);
        metrics.recordTaskProcessed();
        metrics.recordSuccessfulTask();
        
        // Estimate energy consumption (simplified model)
        double energyConsumed = estimateEnergyConsumption(tuple, srcModuleId, dstModuleId);
        metrics.recordEnergyConsumption(energyConsumed);
    }
    
    /**
     * Estimate energy consumption for processing a tuple
     * @param tuple Tuple being processed
     * @param srcModuleId Source module ID
     * @param dstModuleId Destination module ID
     * @return Estimated energy consumption in Joules
     */
    private double estimateEnergyConsumption(Tuple tuple, int srcModuleId, int dstModuleId) {
        // Simple energy model:
        // - Base energy for computation
        // - Additional energy based on tuple size for data transfer
        double baseEnergy = 0.1; // Base computation energy in Joules
        double tupleSize = tuple.getCloudletFileSize() + tuple.getCloudletOutputSize();
        double transferEnergyPerByte = 0.00001; // Energy per byte transferred
        
        // Find source and destination devices
        FogDevice srcDevice = null;
        FogDevice dstDevice = null;
        
        for(FogDevice device : getFogDevices()) {
            if(device.getId() == srcModuleId) {
                srcDevice = device;
            }
            if(device.getId() == dstModuleId) {
                dstDevice = device;
            }
        }
        
        // Add transfer energy if devices are different
        double transferEnergy = 0;
        if(srcDevice != null && dstDevice != null && srcDevice.getId() != dstDevice.getId()) {
            transferEnergy = tupleSize * transferEnergyPerByte;
        }
        
        return baseEnergy + transferEnergy;
    }
    
    /**
     * Get the collected metrics
     * @return TaskOffloadingMetrics object
     */
    public TaskOffloadingMetrics getMetrics() {
        return metrics;
    }
}
