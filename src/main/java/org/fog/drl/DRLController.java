package org.fog.drl;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.core.predicates.Predicate;
import org.fog.application.Application;
import org.fog.entities.*;
import org.fog.placement.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DRL Controller for Task Offloading in Mobile Edge Computing
 * This controller implements Deep Reinforcement Learning for task offloading decisions
 * Based on the paper: "Deep Reinforcement Learning for Task Offloading in Mobile Edge Computing" (Wang et al., IEEE INFOCOM 2020)
 */
public class DRLController extends Controller {
    
    // DRL Agent to make offloading decisions
    private DRLAgent agent;
    
    // Mapping of modules to devices, updated by the DRL agent
    private Map<String, Integer> moduleToDeviceMap;
    
    // Performance tracking
    private Map<String, Double> moduleLatencies;
    private Map<String, Double> deviceEnergyConsumption;
    
    // Custom constants for simulation events
    private static final int DRL_MAKE_DECISION = 1000;
    private static final int DRL_UPDATE_MODEL = 1001;
    
    /**
     * Constructor
     * @param name Controller name
     * @param fogDevices List of fog devices in the system
     * @param sensors List of sensors in the system
     * @param actuators List of actuators in the system
     * @param applications List of applications
     */
    public DRLController(String name, List<FogDevice> fogDevices, List<Sensor> sensors, 
                         List<Actuator> actuators, Application application) {
        super(name, fogDevices, sensors, actuators, application);
        this.moduleToDeviceMap = new HashMap<>();
        this.moduleLatencies = new HashMap<>();
        this.deviceEnergyConsumption = new HashMap<>();
        
        // Initialize the DRL agent
        this.agent = new DRLAgent(fogDevices, application);
        
        // Schedule the first decision-making event
        schedule(getId(), 10, DRL_MAKE_DECISION);
    }
    
    @Override
    public void startEntity() {
        super.startEntity();
        System.out.println("Starting DRL Controller...");
    }
    
    @Override
    public void processEvent(SimEvent ev) {
        switch (ev.getTag()) {
            case DRL_MAKE_DECISION:
                processDRLDecision();
                break;
            case DRL_UPDATE_MODEL:
                updateDRLModel();
                break;
            default:
                super.processEvent(ev);
                break;
        }
    }
    
    /**
     * Process DRL decision-making for task offloading
     */
    private void processDRLDecision() {
        System.out.println("DRL Controller making offloading decisions at time " + CloudSim.clock());
        
        // Get the current state of the environment
        EnvironmentState currentState = getEnvironmentState();
        
        // Get offloading decisions from the DRL agent
        Map<String, Integer> offloadingDecisions = agent.makeOffloadingDecisions(currentState);
        
        // Apply the decisions
        applyOffloadingDecisions(offloadingDecisions);
        
        // Schedule the next decision-making event
        schedule(getId(), 100, DRL_MAKE_DECISION);
        
        // Schedule model update
        schedule(getId(), 50, DRL_UPDATE_MODEL);
    }
    
    /**
     * Update the DRL model based on observed rewards
     */
    private void updateDRLModel() {
        System.out.println("Updating DRL model at time " + CloudSim.clock());
        
        // Calculate rewards based on performance metrics
        double reward = calculateReward();
        
        // Get the current state
        EnvironmentState currentState = getEnvironmentState();
        
        // Update the DRL agent with the observed reward
        agent.updateModel(reward, currentState);
    }
    
    /**
     * Get the current state of the environment
     * @return Current environment state
     */
    private EnvironmentState getEnvironmentState() {
        EnvironmentState state = new EnvironmentState();
        
        // Add resource utilization information for each device
        for (FogDevice device : getFogDevices()) {
            double cpuUtilization = device.getHost().getCpuUtilization();
            double memoryUtilization = device.getHost().getRam() > 0 ? 
                    (device.getHost().getRamProvisioner().getUsedRam() * 100.0 / device.getHost().getRam()) : 0;
            
            state.addDeviceState(device.getId(), cpuUtilization, memoryUtilization);
        }
        
        // Add network information
        for (FogDevice device : getFogDevices()) {
            if (device.getParentId() != -1) {
                state.addNetworkState(device.getId(), device.getParentId(), device.getUplinkLatency());
            }
        }
        
        // Add task information
        for (String moduleName : getApplication().getModules()) {
            state.addTaskState(moduleName, 
                    getApplication().getModuleByName(moduleName).getMips(), 
                    getApplication().getModuleByName(moduleName).getRam());
        }
        
        return state;
    }
    
    /**
     * Apply offloading decisions from the DRL agent
     * @param offloadingDecisions Map of module names to device IDs
     */
    private void applyOffloadingDecisions(Map<String, Integer> offloadingDecisions) {
        // Update module to device mapping
        moduleToDeviceMap.putAll(offloadingDecisions);
        
        // Apply decisions to the actual placement
        for (Map.Entry<String, Integer> entry : offloadingDecisions.entrySet()) {
            String moduleName = entry.getKey();
            int deviceId = entry.getValue();
            
            // Find the device
            FogDevice targetDevice = null;
            for (FogDevice device : getFogDevices()) {
                if (device.getId() == deviceId) {
                    targetDevice = device;
                    break;
                }
            }
            
            if (targetDevice != null) {
                System.out.println("Offloading module " + moduleName + " to device " + targetDevice.getName());
                // In a real implementation, we would update the actual placement here
                // This is a simplified version for the simulation
            }
        }
    }
    
    /**
     * Calculate reward based on system performance
     * @return Calculated reward value
     */
    private double calculateReward() {
        double totalLatency = 0;
        double totalEnergy = 0;
        
        // Calculate total latency from all application modules
        for (String moduleName : moduleLatencies.keySet()) {
            totalLatency += moduleLatencies.get(moduleName);
        }
        
        // Calculate total energy consumption from all devices
        for (String deviceName : deviceEnergyConsumption.keySet()) {
            totalEnergy += deviceEnergyConsumption.get(deviceName);
        }
        
        // Calculate reward (negative cost) based on weighted combination of latency and energy
        // Lower latency and energy consumption results in higher reward
        double latencyWeight = 0.6;  // Weight for latency in reward function
        double energyWeight = 0.4;   // Weight for energy in reward function
        
        // Normalize values (assuming some reasonable maximum values)
        double normalizedLatency = Math.min(totalLatency / 1000.0, 1.0);
        double normalizedEnergy = Math.min(totalEnergy / 500.0, 1.0);
        
        // Calculate reward as negative weighted sum of normalized costs
        double reward = -1.0 * (latencyWeight * normalizedLatency + energyWeight * normalizedEnergy);
        
        System.out.println("Reward calculated: " + reward);
        return reward;
    }
    
    /**
     * Update latency measurements for a specific module
     * @param moduleName Module name
     * @param latency Measured latency
     */
    public void updateModuleLatency(String moduleName, double latency) {
        moduleLatencies.put(moduleName, latency);
    }
    
    /**
     * Update energy consumption for a specific device
     * @param deviceName Device name
     * @param energy Measured energy consumption
     */
    public void updateDeviceEnergy(String deviceName, double energy) {
        deviceEnergyConsumption.put(deviceName, energy);
    }
}
