package org.fog.drl;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.fog.entities.FogDevice;
import java.util.Collections;

/**
 * Deep Reinforcement Learning Agent for task offloading decisions
 * Implements Deep Q-Network (DQN) with experience replay and target network
 */
public class DRLAgent {
    // Neural Networks
    private NeuralNetwork qNetwork;       // Main Q-network
    private NeuralNetwork targetNetwork;  // Target network for stable learning
    
    // Experience replay
    private ReplayBuffer replayBuffer;
    private int batchSize = 32;
    private int updateFrequency = 10;     // Update target network every N steps
    
    // Exploration parameters
    private double epsilon;               // Exploration rate
    private double epsilonMin;            // Minimum exploration rate
    private double epsilonDecay;          // Decay rate for epsilon
    
    // Learning parameters
    private double gamma;                 // Discount factor
    private int stepCount;                // Counter for update frequency
    
    // Action space (list of available devices for offloading)
    private List<String> deviceNames;
    private List<FogDevice> devices;
    
    // Random number generator
    private Random random;
    
    /**
     * Constructor for DRL agent
     * @param devices Available fog devices for offloading
     * @param stateSize Size of the state feature vector
     */
    public DRLAgent(List<FogDevice> devices, int stateSize) {
        this.devices = new ArrayList<>(devices);
        this.deviceNames = new ArrayList<>();
        
        // Extract device names
        for (FogDevice device : devices) {
            deviceNames.add(device.getName());
        }
        
        int actionSize = devices.size();
        
        // Initialize neural networks
        this.qNetwork = new NeuralNetwork(stateSize, 24, actionSize);
        this.targetNetwork = new NeuralNetwork(stateSize, 24, actionSize);
        this.targetNetwork.copyParameters(qNetwork);  // Initialize target with same weights
        
        // Initialize replay buffer
        this.replayBuffer = new ReplayBuffer(1000);
        
        // Set hyperparameters
        this.epsilon = 1.0;        // Start with full exploration
        this.epsilonMin = 0.1;     // Minimum exploration rate
        this.epsilonDecay = 0.995; // Decay rate for epsilon
        this.gamma = 0.95;         // Discount factor
        this.stepCount = 0;
        
        this.random = new Random();
    }
    
    /**
     * Select an action using epsilon-greedy policy
     * @param state Current environment state
     * @return Selected action (index of the device)
     */
    public int selectAction(double[] state) {
        // Exploration: select random action
        if (random.nextDouble() < epsilon) {
            return random.nextInt(deviceNames.size());
        }
        
        // Exploitation: select best action according to Q-network
        double[] qValues = qNetwork.forward(state);
        return argmax(qValues);
    }
    
    /**
     * Find the index with maximum value in an array
     * @param array Array of values
     * @return Index of maximum value
     */
    private int argmax(double[] array) {
        int bestIndex = 0;
        double bestValue = array[0];
        
        for (int i = 1; i < array.length; i++) {
            if (array[i] > bestValue) {
                bestValue = array[i];
                bestIndex = i;
            }
        }
        
        return bestIndex;
    }
    
    /**
     * Train the agent using a batch of experiences
     * @return Average loss
     */
    public double train() {
        // Skip if not enough experiences
        if (replayBuffer.size() < batchSize) {
            return 0.0;
        }
        
        double totalLoss = 0.0;
        
        // Sample a batch of experiences
        List<Experience> batch = replayBuffer.sample(batchSize);
        
        for (Experience experience : batch) {
            double[] state = experience.getState();
            int action = experience.getAction();
            double reward = experience.getReward();
            double[] nextState = experience.getNextState();
            boolean terminal = experience.isTerminal();
            
            // Current Q-values
            double[] currentQValues = qNetwork.forward(state);
            double[] targetQValues = currentQValues.clone();
            
            if (terminal) {
                // If terminal state, the target is just the reward
                targetQValues[action] = reward;
            } else {
                // Otherwise, target = reward + gamma * max(Q(s', a'))
                double[] nextQValues = targetNetwork.forward(nextState);
                double maxNextQ = Double.NEGATIVE_INFINITY;
                for (double qValue : nextQValues) {
                    maxNextQ = Math.max(maxNextQ, qValue);
                }
                targetQValues[action] = reward + gamma * maxNextQ;
            }
            
            // Train the Q-network
            totalLoss += qNetwork.train(state, targetQValues);
        }
        
        // Update target network periodically
        stepCount++;
        if (stepCount % updateFrequency == 0) {
            targetNetwork.copyParameters(qNetwork);
        }
        
        // Decay epsilon
        epsilon = Math.max(epsilonMin, epsilon * epsilonDecay);
        
        return totalLoss / batchSize;
    }
    
    /**
     * Store an experience in the replay buffer
     * @param state Current state
     * @param action Selected action
     * @param reward Received reward
     * @param nextState Next state
     * @param terminal Whether it's a terminal state
     */
    public void remember(double[] state, int action, double reward, double[] nextState, boolean terminal) {
        Experience experience = new Experience(state, action, reward, nextState, terminal);
        replayBuffer.add(experience);
    }
    
    /**
     * Get the device corresponding to an action index
     * @param actionIndex Index of the selected action
     * @return Corresponding FogDevice
     */
    public FogDevice getDeviceForAction(int actionIndex) {
        if (actionIndex < 0 || actionIndex >= devices.size()) {
            throw new IllegalArgumentException("Invalid action index: " + actionIndex);
        }
        return devices.get(actionIndex);
    }
    
    /**
     * Get the name of the device for an action index
     * @param actionIndex Index of the selected action
     * @return Name of the corresponding device
     */
    public String getDeviceNameForAction(int actionIndex) {
        if (actionIndex < 0 || actionIndex >= deviceNames.size()) {
            throw new IllegalArgumentException("Invalid action index: " + actionIndex);
        }
        return deviceNames.get(actionIndex);
    }
    
    /**
     * Get the current exploration rate
     * @return Current value of epsilon
     */
    public double getEpsilon() {
        return epsilon;
    }
    
    /**
     * Get the list of device names
     * @return List of device names
     */
    public List<String> getDeviceNames() {
        return Collections.unmodifiableList(deviceNames);
    }
    
    /**
     * Get the number of experiences in the replay buffer
     * @return Size of the replay buffer
     */
    public int getReplayBufferSize() {
        return replayBuffer.size();
    }
    
    /**
     * Set the hyperparameters for the agent
     * @param epsilon Initial exploration rate
     * @param epsilonMin Minimum exploration rate
     * @param epsilonDecay Decay rate for epsilon
     * @param gamma Discount factor
     * @param updateFrequency Frequency for updating target network
     */
    public void setHyperparameters(double epsilon, double epsilonMin, double epsilonDecay, 
                                  double gamma, int updateFrequency) {
        this.epsilon = epsilon;
        this.epsilonMin = epsilonMin;
        this.epsilonDecay = epsilonDecay;
        this.gamma = gamma;
        this.updateFrequency = updateFrequency;
    }
    
    /**
     * Set the batch size for training
     * @param batchSize New batch size
     */
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }
}
