package org.fog.drl;

import org.fog.entities.FogDevice;
import org.fog.application.AppModule;
import org.cloudbus.cloudsim.Vm;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * DRLTaskOffloadingAgent implements a Deep Reinforcement Learning agent
 * for task offloading decisions in Mobile Edge Computing environments.
 */
public class DRLTaskOffloadingAgent {
    // Hyperparameters
    private final double LEARNING_RATE = 0.001;
    private final double DISCOUNT_FACTOR = 0.95;
    private final double EPSILON_INITIAL = 1.0;
    private final double EPSILON_DECAY = 0.995;
    private final double EPSILON_MIN = 0.01;
    private final int BATCH_SIZE = 32;
    private final int TARGET_UPDATE_FREQUENCY = 1000;

    // DRL components
    private NeuralNetwork mainNetwork;
    private NeuralNetwork targetNetwork;
    private ReplayBuffer replayBuffer;
    private double epsilon;
    private int stepCount;
    
    // Environment mappings
    private List<String> availableDevices;
    private List<String> applicationModules;
    private Map<String, Integer> deviceToIndex;
    private Map<String, Integer> moduleToIndex;

    /**
     * Constructor initializes the DRL agent with available devices and application modules
     * @param fogDevices List of available fog devices for offloading
     * @param appModules List of application modules to be placed
     */
    public DRLTaskOffloadingAgent(List<FogDevice> fogDevices, List<AppModule> appModules) {
        // Initialize environment mappings
        this.availableDevices = new ArrayList<>();
        this.applicationModules = new ArrayList<>();
        this.deviceToIndex = new HashMap<>();
        this.moduleToIndex = new HashMap<>();
        
        // Map fog devices and application modules to indices
        int index = 0;
        for (FogDevice device : fogDevices) {
            String deviceName = device.getName();
            this.availableDevices.add(deviceName);
            this.deviceToIndex.put(deviceName, index++);
        }
        
        index = 0;
        for (AppModule module : appModules) {
            String moduleName = module.getName();
            this.applicationModules.add(moduleName);
            this.moduleToIndex.put(moduleName, index++);
        }
        
        // Initialize DRL components
        int stateSize = createStateVector(null, null, null).length;
        int actionSize = fogDevices.size();
        // Initialize the neural networks with a hidden layer
        int hiddenLayerSize = 24;  // You can adjust this
        this.mainNetwork = new NeuralNetwork(stateSize, hiddenLayerSize, actionSize);
        this.targetNetwork = new NeuralNetwork(stateSize, hiddenLayerSize, actionSize);
        updateTargetNetwork(); // Initial copy of weights
        
        this.replayBuffer = new ReplayBuffer(10000); // Store 10,000 experiences
        this.epsilon = EPSILON_INITIAL;
        this.stepCount = 0;
    }
    
    /**
     * Creates a state vector representing the current environment state
     * @param module The application module to be placed
     * @param deviceUtilization Map of device utilization levels
     * @param networkLatencies Map of network latencies between devices
     * @return A vector representing the state
     */
    private double[] createStateVector(AppModule module, Map<String, Double> deviceUtilization, 
                                     Map<String, Map<String, Double>> networkLatencies) {
        // This is a simplified state representation
        // In a real implementation, you would include more features:
        // - Module computational requirements
        // - Current CPU/RAM utilization of devices
        // - Network conditions between devices
        // - Battery levels of mobile devices
        // - Etc.
        
        // For now, we'll create a simple state vector with:
        // 1. Module one-hot encoding
        // 2. Device utilization values
        
        int stateSize = applicationModules.size() + availableDevices.size();
        double[] state = new double[stateSize];
        
        // If this is just to get the state size (during initialization)
        if (module == null) {
            return state;
        }
        
        // Set the module one-hot encoding
        int moduleIdx = moduleToIndex.get(module.getName());
        state[moduleIdx] = 1.0;
        
        // Set the device utilization values
        for (int i = 0; i < availableDevices.size(); i++) {
            String device = availableDevices.get(i);
            double utilization = deviceUtilization.getOrDefault(device, 0.0);
            state[applicationModules.size() + i] = utilization;
        }
        
        return state;
    }
    
    /**
     * Selects an action (target device) for placing a module using epsilon-greedy policy
     * @param state The current state vector
     * @return The selected action index
     */
    private int selectAction(double[] state) {
        // Epsilon-greedy action selection
        if (Math.random() < epsilon) {
            // Exploration: select random action
            return ThreadLocalRandom.current().nextInt(availableDevices.size());
        } else {
            // Exploitation: select best action according to Q-values
            double[] qValues = mainNetwork.predict(state);
            int bestAction = 0;
            double bestValue = qValues[0];
            
            for (int i = 1; i < qValues.length; i++) {
                if (qValues[i] > bestValue) {
                    bestValue = qValues[i];
                    bestAction = i;
                }
            }
            
            return bestAction;
        }
    }
    
    /**
     * Updates the target network with the weights from the main network
     */
    private void updateTargetNetwork() {
        targetNetwork.copyParameters(mainNetwork);
    }
    
    /**
     * Trains the DRL agent on a batch of experiences from the replay buffer
     */
    private void trainNetwork() {
        // Skip if we don't have enough samples
        if (replayBuffer.size() < BATCH_SIZE) {
            return;
        }
        
        // Sample a batch of experiences
        List<Experience> batch = replayBuffer.sample(BATCH_SIZE);
        
        // Prepare training data
        double[][] states = new double[BATCH_SIZE][];
        double[][] targets = new double[BATCH_SIZE][];
        
        // Compute targets for each experience in the batch
        for (int i = 0; i < BATCH_SIZE; i++) {
            Experience exp = batch.get(i);
            states[i] = exp.getState();
            
            // Get current Q-values for the state
            double[] qValues = mainNetwork.predict(exp.getState());
            
            // Compute target Q-value for the taken action
            if (exp.isTerminal()) {
                // Terminal state: target is just the reward
                qValues[exp.getAction()] = exp.getReward();
            } else {
                // Non-terminal: target is reward + discounted future value
                double[] nextQValues = targetNetwork.predict(exp.getNextState());
                double maxNextQ = Arrays.stream(nextQValues).max().orElse(0.0);
                qValues[exp.getAction()] = exp.getReward() + DISCOUNT_FACTOR * maxNextQ;
            }
            
            targets[i] = qValues;
        }
        
        // Train the network
        mainNetwork.train(states, targets, LEARNING_RATE);
        
        // Update target network periodically
        if (stepCount % TARGET_UPDATE_FREQUENCY == 0) {
            updateTargetNetwork();
        }
        
        // Decay epsilon
        if (epsilon > EPSILON_MIN) {
            epsilon *= EPSILON_DECAY;
        }
    }
    
    /**
     * Returns the best device for a given module based on current policy
     * @param module The application module to be placed
     * @param deviceUtilization Current utilization of all devices
     * @param networkLatencies Network latencies between devices
     * @return The name of the selected device
     */
    public String selectDeviceForModule(AppModule module, Map<String, Double> deviceUtilization,
                                       Map<String, Map<String, Double>> networkLatencies) {
        // Create state vector
        double[] state = createStateVector(module, deviceUtilization, networkLatencies);
        
        // Select action
        int actionIndex = selectAction(state);
        
        // Return the selected device name
        return availableDevices.get(actionIndex);
    }
    
    /**
     * Updates the agent with the results of a placement decision
     * @param module The placed application module
     * @param deviceName The device where the module was placed
     */
    public void storeExperience(double[] state, int actionIndex, double reward, double[] nextState, boolean isTerminal) {
        Experience experience = new Experience(state, actionIndex, reward, nextState, isTerminal);
        replayBuffer.add(experience);
    }
    
    /**
     * Update the agent with a new experience and train the network
     * @param module The application module
     * @param deviceName The device name where the module was placed
     * @param state Current state
     * @param nextState Next state
     * @param reward Reward received
     * @param isDone Whether this is a terminal state
     */
    public void updateAgent(AppModule module, String deviceName, double[] state, double[] nextState, double reward, boolean isDone) {
        // Get action index from device name
        int actionIndex = deviceToIndex.get(deviceName);
        
        storeExperience(state, actionIndex, reward, nextState, isDone);
        
        // Train the network
        trainNetwork();
        
        // Increment step count
        stepCount++;
    }
    
    /**
     * Computes a reward for a placement decision based on various metrics
     * @param deviceName The device where the module was placed
     * @param module The placed application module
     * @param executionTime Execution time of the module
     * @param energyConsumption Energy consumed
     * @param networkUsage Network bandwidth used
     * @return A scalar reward value
     */
    public double computeReward(String deviceName, AppModule module, double executionTime, 
                              double energyConsumption, double networkUsage) {
        // Weights for different components of the reward
        final double TIME_WEIGHT = 0.5;
        final double ENERGY_WEIGHT = 0.3;
        final double NETWORK_WEIGHT = 0.2;
        
        // Normalize values (these would need to be calibrated for your system)
        double normalizedTime = Math.min(1.0, executionTime / 100.0); // Assume max time is 100ms
        double normalizedEnergy = Math.min(1.0, energyConsumption / 1000.0); // Assume max energy is 1000 units
        double normalizedNetwork = Math.min(1.0, networkUsage / 500.0); // Assume max network is 500 units
        
        // Compute penalty (negative reward is a penalty)
        double penalty = TIME_WEIGHT * normalizedTime + 
                        ENERGY_WEIGHT * normalizedEnergy + 
                        NETWORK_WEIGHT * normalizedNetwork;
        
        // Return negative penalty as reward (higher is better)
        return -penalty;
    }
    
    /**
     * Returns the current learned Q-values for a specific module across all devices
     * @param module The application module
     * @param deviceUtilization Current device utilization
     * @param networkLatencies Network latencies between devices
     * @return Map of device name to Q-value
     */
    public Map<String, Double> getQValuesForModule(AppModule module, Map<String, Double> deviceUtilization,
                                                Map<String, Map<String, Double>> networkLatencies) {
        // Create state vector
        double[] state = createStateVector(module, deviceUtilization, networkLatencies);
        
        // Get Q-values from neural network
        double[] qValues = mainNetwork.predict(state);
        
        // Map Q-values to device names
        Map<String, Double> deviceQValues = new HashMap<>();
        for (int i = 0; i < availableDevices.size(); i++) {
            deviceQValues.put(availableDevices.get(i), qValues[i]);
        }
        
        return deviceQValues;
    }
}
