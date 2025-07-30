package org.fog.drl;

import org.fog.application.Application;
import org.fog.entities.FogDevice;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Deep Reinforcement Learning Agent for Task Offloading decisions
 * This implementation is based on DQN (Deep Q-Network) as described in the paper
 * "Deep Reinforcement Learning for Task Offloading in Mobile Edge Computing" (Wang et al., IEEE INFOCOM 2020)
 */
public class DRLAgent {
    
    // List of available fog devices
    private List<FogDevice> fogDevices;
    
    // Application with modules that need placement decisions
    private Application application;
    
    // Neural Network for DQN
    private NeuralNetwork qNetwork;
    private NeuralNetwork targetNetwork;
    
    // Experience replay buffer
    private ReplayBuffer replayBuffer;
    
    // Hyperparameters
    private double learningRate = 0.001;
    private double discountFactor = 0.95;
    private double explorationRate = 0.1;
    private int batchSize = 32;
    private int targetUpdateFrequency = 100;
    private int updateCounter = 0;
    
    // Previous state and action for learning
    private EnvironmentState previousState;
    private Map<String, Integer> previousActions;
    
    /**
     * Constructor
     * @param fogDevices List of available fog devices
     * @param application Application with modules to place
     */
    public DRLAgent(List<FogDevice> fogDevices, Application application) {
        this.fogDevices = fogDevices;
        this.application = application;
        
        // Initialize Neural Networks
        int stateSize = calculateStateSize();
        int actionSize = fogDevices.size();
        
        this.qNetwork = new NeuralNetwork(stateSize, actionSize, learningRate);
        this.targetNetwork = new NeuralNetwork(stateSize, actionSize, learningRate);
        
        // Copy weights from Q-network to target network
        targetNetwork.copyWeightsFrom(qNetwork);
        
        // Initialize replay buffer
        this.replayBuffer = new ReplayBuffer(10000);
        
        this.previousState = null;
        this.previousActions = new HashMap<>();
    }
    
    /**
     * Calculate the size of the state vector
     * @return State vector size
     */
    private int calculateStateSize() {
        // State includes:
        // - For each device: CPU utilization and memory utilization (2 values per device)
        // - For each network link: latency (1 value per link)
        // - For each task: CPU requirement and memory requirement (2 values per task)
        
        int deviceStateSize = fogDevices.size() * 2;
        int networkStateSize = fogDevices.size(); // Simplified, assuming each device has one uplink
        int taskStateSize = application.getModules().size() * 2;
        
        return deviceStateSize + networkStateSize + taskStateSize;
    }
    
    /**
     * Make offloading decisions for all application modules
     * @param state Current environment state
     * @return Map of module names to device IDs
     */
    public Map<String, Integer> makeOffloadingDecisions(EnvironmentState state) {
        Map<String, Integer> decisions = new HashMap<>();
        
        // Store the current state for learning
        previousState = state;
        
        // Make decisions for each module in the application
        for (String moduleName : application.getModules()) {
            // Skip modules that don't need placement (e.g., already placed at a fixed location)
            if (application.getSpecialPlacementInfo().containsKey(moduleName)) {
                continue;
            }
            
            // Convert state to feature vector specific to this module
            double[] stateVector = state.toFeatureVector(moduleName);
            
            // Epsilon-greedy action selection
            int selectedDeviceIdx;
            if (Math.random() < explorationRate) {
                // Explore: choose random device
                selectedDeviceIdx = ThreadLocalRandom.current().nextInt(0, fogDevices.size());
            } else {
                // Exploit: choose device with highest Q-value
                double[] qValues = qNetwork.predict(stateVector);
                selectedDeviceIdx = argmax(qValues);
            }
            
            // Convert device index to device ID
            int selectedDeviceId = fogDevices.get(selectedDeviceIdx).getId();
            
            // Store the decision
            decisions.put(moduleName, selectedDeviceId);
        }
        
        // Store current actions for learning
        previousActions = new HashMap<>(decisions);
        
        return decisions;
    }
    
    /**
     * Update the DRL model based on observed reward
     * @param reward Reward value
     * @param currentState New environment state after action execution
     */
    public void updateModel(double reward, EnvironmentState currentState) {
        // Skip if this is the first update (no previous state/action)
        if (previousState == null) {
            return;
        }
        
        // For each module placement decision, update the model
        for (String moduleName : previousActions.keySet()) {
            // Skip modules with fixed placement
            if (application.getSpecialPlacementInfo().containsKey(moduleName)) {
                continue;
            }
            
            // Get the device ID from previous action
            int deviceId = previousActions.get(moduleName);
            
            // Find the device index
            int deviceIdx = -1;
            for (int i = 0; i < fogDevices.size(); i++) {
                if (fogDevices.get(i).getId() == deviceId) {
                    deviceIdx = i;
                    break;
                }
            }
            
            if (deviceIdx == -1) {
                continue; // Skip if device not found
            }
            
            // Create experience tuple (s, a, r, s') and store in replay buffer
            double[] prevStateVector = previousState.toFeatureVector(moduleName);
            double[] currentStateVector = currentState.toFeatureVector(moduleName);
            
            replayBuffer.add(new Experience(prevStateVector, deviceIdx, reward, currentStateVector));
        }
        
        // Perform mini-batch training
        if (replayBuffer.size() >= batchSize) {
            trainNetwork();
        }
        
        // Periodically update target network
        updateCounter++;
        if (updateCounter % targetUpdateFrequency == 0) {
            targetNetwork.copyWeightsFrom(qNetwork);
            System.out.println("Target network updated at step " + updateCounter);
        }
    }
    
    /**
     * Train the neural network using mini-batch from replay buffer
     */
    private void trainNetwork() {
        // Sample mini-batch from replay buffer
        List<Experience> batch = replayBuffer.sample(batchSize);
        
        for (Experience exp : batch) {
            // Get current Q values
            double[] currentQValues = qNetwork.predict(exp.getState());
            
            // Get next state's maximum Q value using target network
            double[] nextQValues = targetNetwork.predict(exp.getNextState());
            double maxNextQ = max(nextQValues);
            
            // Calculate target Q value for the action taken
            double targetQ = exp.getReward() + discountFactor * maxNextQ;
            
            // Update only the Q value for the action taken
            currentQValues[exp.getAction()] = targetQ;
            
            // Train the network with the updated Q values
            qNetwork.train(exp.getState(), currentQValues);
        }
    }
    
    /**
     * Find index of maximum value in array
     * @param values Array of values
     * @return Index of maximum value
     */
    private int argmax(double[] values) {
        int maxIndex = 0;
        double maxValue = values[0];
        
        for (int i = 1; i < values.length; i++) {
            if (values[i] > maxValue) {
                maxValue = values[i];
                maxIndex = i;
            }
        }
        
        return maxIndex;
    }
    
    /**
     * Find maximum value in array
     * @param values Array of values
     * @return Maximum value
     */
    private double max(double[] values) {
        double maxValue = values[0];
        
        for (int i = 1; i < values.length; i++) {
            if (values[i] > maxValue) {
                maxValue = values[i];
            }
        }
        
        return maxValue;
    }
}
