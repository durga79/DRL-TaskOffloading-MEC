package org.fog.drl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.entities.Actuator;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacement;
import org.fog.placement.ModulePlacementMapping;
import org.fog.utils.TimeKeeper;

/**
 * DRL Controller that integrates the DRL agent with the iFogSim framework
 * Extends the base Controller to make DRL-based task offloading decisions
 */
public class DRLController extends Controller {
    private DRLAgent agent;
    private EnvironmentState envState;
    private Map<String, Integer> moduleToDeviceMap;
    private boolean trainingMode = true;
    private int stateSize;
    private int episodeCount = 0;
    private int maxEpisodes = 1000;
    
    // Metrics for evaluation
    private Map<String, Double> moduleLatencies;
    private Map<String, Double> moduleEnergyConsumption;
    private double cumulativeReward = 0.0;
    private int placementCount = 0;
    
    /**
     * Constructor for DRL Controller
     * @param name Controller name
     * @param fogDevices List of fog devices in the system
     * @param sensors List of sensors
     * @param actuators List of actuators
     */
    public DRLController(String name, List<FogDevice> fogDevices, List<Sensor> sensors, List<Actuator> actuators) {
        super(name, fogDevices, sensors, actuators);
        
        this.moduleToDeviceMap = new HashMap<>();
        this.moduleLatencies = new HashMap<>();
        this.moduleEnergyConsumption = new HashMap<>();
        
        // Initialize environment state
        envState = new EnvironmentState();
        
        // Calculate state size based on number of devices and features per device
        stateSize = fogDevices.size() * 5; // 5 features per device
        
        // Initialize the DRL agent
        agent = new DRLAgent(fogDevices, stateSize);
    }
    
    @Override
    public void startEntity() {
        super.startEntity();
        System.out.println("DRL Controller starting...");
    }
    
    @Override
    public void submitApplication(Application application, ModulePlacement modulePlacement) {
        // Call super to use the appropriate iFogSim API methods
        super.submitApplication(application, modulePlacement);
        System.out.println("DRL Controller: Application submitted with " + application.getModules().size() + " modules");
    }
    
    /**
     * Deploy modules with DRL-based decisions for dynamic modules
     * @param application The application to deploy
     * @return ModulePlacement instance with the DRL-based decisions
     */
    public ModulePlacement deployModulesWithDRL(Application application) {
        // Create initial static mapping for non-DRL modules
        ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
        
        // Update environment state with current system information
        updateEnvironmentState(application);
        
        // Apply DRL decisions for each dynamic module
        for (AppModule module : application.getModules()) {
            String moduleName = module.getName();
            
            // Skip modules that have fixed placements
            if (moduleMapping.getModuleMapping().containsKey(moduleName)) {
                continue;
            }
            
            // Get state features for current module
            double[] state = getStateForModule(moduleName);
            
            // Use DRL agent to select device
            int action = agent.selectAction(state);
            FogDevice selectedDevice = agent.getDeviceForAction(action);
            
            // Add module to mapping
            moduleMapping.addModuleToDevice(moduleName, selectedDevice.getName());
            moduleToDeviceMap.put(moduleName, selectedDevice.getId());
            
            // Store decision for reward calculation later
            placementCount++;
            
            System.out.println("DRL Controller: Placed module " + moduleName + " on device " + selectedDevice.getName());
            
            // In training mode, simulate next state and calculate reward
            if (trainingMode) {
                double reward = calculateReward(moduleName, selectedDevice);
                double[] nextState = getStateForModule(moduleName); // Simplified - should actually reflect the new state
                
                // Store experience in replay buffer
                agent.remember(state, action, reward, nextState, false);
                cumulativeReward += reward;
                
                // Train the agent
                if (agent.getReplayBufferSize() >= 32) {
                    double loss = agent.train();
                    System.out.println("DRL Controller: Training loss = " + loss);
                }
            }
        }
        
        // Create and return the module placement strategy
        return new ModulePlacementMapping(getFogDevices(), application, moduleMapping);
    }
    
    /**
     * Update the environment state with current system information
     * @param application Current application
     */
    private void updateEnvironmentState(Application application) {
        envState.updateState(getFogDevices(), application.getModules());
    }
    
    /**
     * Get the state features for a specific module
     * @param moduleName Name of the module
     * @return State feature vector
     */
    private double[] getStateForModule(String moduleName) {
        return envState.toFeatureVector(agent.getDeviceNames(), moduleName);
    }
    
    /**
     * Calculate reward for a placement decision
     * @param moduleName Module name
     * @param device Selected device
     * @return Reward value
     */
    private double calculateReward(String moduleName, FogDevice device) {
        // Calculate reward based on multiple factors
        double latencyReward = 0.0;
        double energyReward = 0.0;
        double loadBalanceReward = 0.0;
        
        // Latency component - inversely proportional to device level (higher level = cloud = higher latency)
        int deviceLevel = device.getLevel();
        latencyReward = -0.2 * deviceLevel; // Negative reward for higher latency
        
        // Energy component - based on device energy consumption
        // Cloud has higher energy but better performance, edge has lower energy
        if (device.getName().startsWith("cloud")) {
            energyReward = -3.0; // Higher energy penalty for cloud
        } else if (device.getName().startsWith("edge")) {
            energyReward = -1.0; // Medium energy penalty for edge
        } else {
            energyReward = -0.5; // Lower energy penalty for mobile
        }
        
        // Load balancing component - reward for using underutilized devices
        double cpuUtilization = envState.getCpuUtilization().getOrDefault(device.getName(), 0.0);
        loadBalanceReward = -2.0 * cpuUtilization; // Negative reward for utilizing busy devices
        
        // Battery consideration for mobile devices
        double batteryPenalty = 0.0;
        if (device.getName().startsWith("mobile")) {
            // Higher penalty for using mobile devices with lower battery
            double batteryLevel = 1.0; // Assume full battery if not found
            for (String deviceName : envState.getCpuUtilization().keySet()) {
                if (deviceName.equals(device.getName())) {
                    // In a real system, we'd get actual battery levels
                    String[] parts = deviceName.split("-");
                    if (parts.length >= 3) {
                        int deviceId = Integer.parseInt(parts[2]);
                        batteryLevel = 1.0 - (deviceId * 0.1); // Simple simulation of battery levels
                    }
                    break;
                }
            }
            batteryPenalty = -2.0 * (1.0 - batteryLevel);
        }
        
        // Combine all reward components
        double totalReward = latencyReward + energyReward + loadBalanceReward + batteryPenalty;
        
        // Store metrics for evaluation
        moduleLatencies.put(moduleName, (double) deviceLevel * 10); // Simplified latency metric
        moduleEnergyConsumption.put(moduleName, -energyReward); // Simplified energy metric
        
        System.out.println("DRL Controller: Reward for placing " + moduleName + 
                           " on " + device.getName() + " = " + totalReward);
        
        return totalReward;
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
        for (Double energy : moduleEnergyConsumption.values()) {
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
     * Set the training mode
     * @param trainingMode Whether the agent is in training mode
     */
    public void setTrainingMode(boolean trainingMode) {
        this.trainingMode = trainingMode;
    }
    
    /**
     * Get the DRL agent
     * @return DRL agent
     */
    public DRLAgent getAgent() {
        return agent;
    }
    
    /**
     * Reset metrics for a new episode
     */
    public void resetEpisodeMetrics() {
        moduleLatencies.clear();
        moduleEnergyConsumption.clear();
        cumulativeReward = 0.0;
        placementCount = 0;
    }
    
    /**
     * Start a new episode
     */
    public void startNewEpisode() {
        episodeCount++;
        resetEpisodeMetrics();
        System.out.println("DRL Controller: Starting episode " + episodeCount + "/" + maxEpisodes);
    }
    
    /**
     * Check if training is complete
     * @return True if training is complete
     */
    public boolean isTrainingComplete() {
        return episodeCount >= maxEpisodes;
    }
}
