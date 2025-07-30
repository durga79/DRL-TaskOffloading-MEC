package org.fog.drl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.FogDevice;
import org.fog.entities.Tuple;
import org.fog.placement.ModulePlacement;
import org.fog.placement.ModuleMapping;

/**
 * Task offloading policy based on Deep Reinforcement Learning
 * Integrates with iFogSim's placement mechanism to make dynamic offloading decisions
 */
public class DRLTaskOffloadingPolicy extends ModulePlacement {
    
    private DRLAgent agent;
    private EnvironmentState envState;
    private Map<String, Integer> moduleToDeviceMap;
    private boolean isFirstPlacement = true;
    
    // Performance metrics
    private Map<String, Double> moduleLatencies;
    private Map<String, Double> moduleEnergies;
    private double cumulativeReward = 0.0;
    private int placementCount = 0;
    
    /**
     * Constructor for the DRL task offloading policy
     * @param fogDevices List of available fog devices
     * @param application Application to deploy
     */
    private ModuleMapping moduleMapping;
    
    public DRLTaskOffloadingPolicy(List<FogDevice> fogDevices, Application application, ModuleMapping moduleMapping) {
        super();
        this.setFogDevices(fogDevices);
        this.setApplication(application);
        this.moduleMapping = moduleMapping;
        
        this.moduleToDeviceMap = new HashMap<>();
        this.moduleLatencies = new HashMap<>();
        this.moduleEnergies = new HashMap<>();
        
        // Initialize environment state
        this.envState = new EnvironmentState();
        
        // Calculate state size based on number of devices and features per device
        int stateSize = fogDevices.size() * 5; // 5 features per device
        
        // Initialize DRL agent
        this.agent = new DRLAgent(fogDevices, stateSize);
        
        // Initialize environment state
        updateEnvironmentState();
        
        System.out.println("DRL Task Offloading Policy initialized with " + fogDevices.size() + " devices");
    }

    @Override
    protected void mapModules() {
        if (isFirstPlacement) {
            // First-time placement of all modules
            performInitialPlacement();
            isFirstPlacement = false;
        } else {
            // Dynamic re-placement in response to changes
            performDynamicReplacement();
        }
    }
    
    /**
     * Initial placement of all modules using DRL
     */
    private void performInitialPlacement() {
        // Get all modules in the application
        List<AppModule> modules = new ArrayList<>(getApplication().getModules());
        
        // Update environment state
        updateEnvironmentState();
        
        System.out.println("DRL Policy: Performing initial placement of " + modules.size() + " modules");
        
        for (AppModule module : modules) {
            String moduleName = module.getName();
            
            // Skip sensor or actuator modules
            if (moduleName.equals("SENSOR") || moduleName.equals("ACTUATOR")) {
                continue;
            }
            
            // Get state features for this module
            double[] state = getStateForModule(moduleName);
            
            // Use DRL agent to select a device
            int action = agent.selectAction(state);
            FogDevice selectedDevice = agent.getDeviceForAction(action);
            
            // Create the module on the selected device
            createModuleInstanceOnDevice(module, selectedDevice);
            moduleToDeviceMap.put(moduleName, selectedDevice.getId());
            
            System.out.println("DRL Policy: Placed module " + moduleName + " on device " + selectedDevice.getName());
            
            // Calculate reward and update DRL agent
            double reward = calculateReward(module, selectedDevice);
            cumulativeReward += reward;
            placementCount++;
            
            // The next state after placement (simplified - just use current state as next state)
            double[] nextState = state.clone();
            
            // Store experience
            agent.remember(state, action, reward, nextState, false);
            
            // Train the agent if enough experiences are collected
            if (agent.getReplayBufferSize() >= 32) {
                double loss = agent.train();
                System.out.println("DRL Policy: Training loss = " + loss);
            }
        }
        
        System.out.println("DRL Policy: Initial placement complete with average reward = " + getAverageReward());
    }
    
    /**
     * Dynamic re-placement of modules in response to system changes
     */
    private void performDynamicReplacement() {
        // Get all modules that might need re-placement
        List<AppModule> modules = new ArrayList<>(getApplication().getModules());
        
        // Update environment state to reflect current conditions
        updateEnvironmentState();
        
        System.out.println("DRL Policy: Performing dynamic replacement check");
        
        for (AppModule module : modules) {
            String moduleName = module.getName();
            
            // Skip sensor or actuator modules
            if (moduleName.equals("SENSOR") || moduleName.equals("ACTUATOR")) {
                continue;
            }
            
            // Get current device for this module
            int currentDeviceId = getCurrentDeviceForModule(moduleName);
            FogDevice currentDevice = null;
            for (FogDevice device : getFogDevices()) {
                if (device.getId() == currentDeviceId) {
                    currentDevice = device;
                    break;
                }
            }
            
            if (currentDevice == null) {
                System.out.println("DRL Policy: Cannot find current device for module " + moduleName);
                continue;
            }
            
            // Get state features
            double[] state = getStateForModule(moduleName);
            
            // Use DRL agent to select a new device
            int action = agent.selectAction(state);
            FogDevice selectedDevice = agent.getDeviceForAction(action);
            
            // If selected device is different from current, migrate the module
            if (selectedDevice.getId() != currentDeviceId) {
                System.out.println("DRL Policy: Migrating module " + moduleName + 
                                   " from " + currentDevice.getName() + 
                                   " to " + selectedDevice.getName());
                
                // Migrate module
                migrateModuleToDevice(module, currentDevice, selectedDevice);
                moduleToDeviceMap.put(moduleName, selectedDevice.getId());
                
                // Calculate reward and update DRL agent
                double reward = calculateReward(module, selectedDevice);
                cumulativeReward += reward;
                placementCount++;
                
                // The next state after migration
                double[] nextState = getStateForModule(moduleName);
                
                // Store experience
                agent.remember(state, action, reward, nextState, false);
                
                // Train the agent
                if (agent.getReplayBufferSize() >= 32) {
                    double loss = agent.train();
                    System.out.println("DRL Policy: Training loss = " + loss);
                }
            }
        }
    }
    
    /**
     * Update environment state with current system information
     */
    private void updateEnvironmentState() {
        envState.updateState(getFogDevices(), getApplication().getModules());
    }
    
    /**
     * Get state features for a specific module
     * @param moduleName Module name
     * @return State feature vector
     */
    private double[] getStateForModule(String moduleName) {
        return envState.toFeatureVector(agent.getDeviceNames(), moduleName);
    }
    
    /**
     * Calculate reward for placing a module on a device
     * @param module The module being placed
     * @param device The selected device
     * @return Reward value
     */
    private double calculateReward(AppModule module, FogDevice device) {
        String moduleName = module.getName();
        
        // Latency component - inversely proportional to device level
        int deviceLevel = device.getLevel();
        double latencyReward = -0.2 * deviceLevel; // Negative reward for higher latency
        
        // Energy component - based on device type
        double energyReward = 0.0;
        if (device.getName().startsWith("cloud")) {
            energyReward = -3.0; // Higher energy penalty for cloud
        } else if (device.getName().startsWith("edge")) {
            energyReward = -1.0; // Medium energy penalty for edge
        } else {
            energyReward = -0.5; // Lower energy penalty for mobile
        }
        
        // Load balancing component
        double cpuUtilization = envState.getCpuUtilization().getOrDefault(device.getName(), 0.0);
        double loadBalanceReward = -2.0 * cpuUtilization; // Negative reward for utilizing busy devices
        
        // Battery consideration for mobile devices
        double batteryPenalty = 0.0;
        if (device.getName().startsWith("mobile")) {
            // Simulate battery levels based on device ID
            String[] parts = device.getName().split("-");
            if (parts.length >= 3) {
                int deviceId = Integer.parseInt(parts[2]);
                double batteryLevel = 1.0 - (deviceId * 0.1); // Simple simulation of battery levels
                batteryPenalty = -2.0 * (1.0 - batteryLevel);
            }
        }
        
        // Module resource requirements consideration
        double moduleResourceIntensity = module.getMips() / 1000.0; // Normalize to 0-1 range
        double resourceReward = -moduleResourceIntensity * deviceLevel; // Higher penalty for resource-intensive modules on higher-level devices
        
        // Combine all reward components
        double totalReward = latencyReward + energyReward + loadBalanceReward + batteryPenalty + resourceReward;
        
        // Store metrics for evaluation
        moduleLatencies.put(moduleName, (double) deviceLevel * 10); // Simplified latency metric
        moduleEnergies.put(moduleName, -energyReward); // Simplified energy metric
        
        System.out.println("DRL Policy: Reward for " + moduleName + " on " + device.getName() + " = " + totalReward);
        
        return totalReward;
    }
    
    /**
     * Get the current device ID for a module
     * @param moduleName Module name
     * @return Device ID or -1 if not found
     */
    private int getCurrentDeviceForModule(String moduleName) {
        return moduleToDeviceMap.getOrDefault(moduleName, -1);
    }
    
    /**
     * Migrate a module from one device to another
     * @param module Module to migrate
     * @param sourceDevice Source device
     * @param destinationDevice Destination device
     */
    private void migrateModuleToDevice(AppModule module, FogDevice sourceDevice, FogDevice destinationDevice) {
        // In a real system, this would involve more complex state transfer
        // For simulation, we'll just create a new instance on the destination and remove from source
        
        // Create on destination
        createModuleInstanceOnDevice(module, destinationDevice);
        
        // Remove from source
        // Simplified VM removal due to API limitations
        // In the current iFogSim API version, we can't easily remove VMs by name
        System.out.println("Simulated removal of " + module.getName() + " from " + sourceDevice.getName());
        
        System.out.println("Migrated " + module.getName() + " from " + sourceDevice.getName() + 
                          " to " + destinationDevice.getName());
    }
    
    /**
     * Get the average latency across all module placements
     * @return Average latency
     */
    public double getAverageLatency() {
        if (moduleLatencies.isEmpty()) {
            return 0.0;
        }
        
        double sum = 0.0;
        for (Double latency : moduleLatencies.values()) {
            sum += latency;
        }
        
        return sum / moduleLatencies.size();
    }
    
    /**
     * Get the total energy consumption across all module placements
     * @return Total energy consumption
     */
    public double getTotalEnergyConsumption() {
        double sum = 0.0;
        for (Double energy : moduleEnergies.values()) {
            sum += energy;
        }
        
        return sum;
    }
    
    /**
     * Get the average reward per placement decision
     * @return Average reward
     */
    public double getAverageReward() {
        return placementCount > 0 ? cumulativeReward / placementCount : 0.0;
    }
    
    /**
     * Get the DRL agent
     * @return The DRL agent
     */
    public DRLAgent getDRLAgent() {
        return agent;
    }
}
