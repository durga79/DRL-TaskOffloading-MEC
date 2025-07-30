package org.fog.drl;

import java.util.Arrays;

/**
 * Represents an experience tuple (state, action, reward, next_state) for reinforcement learning
 * Used by the ReplayBuffer for experience replay in DQN
 */
public class Experience {
    
    // Components of an experience tuple
    private double[] state;
    private int action;
    private double reward;
    private double[] nextState;
    
    /**
     * Constructor
     * @param state Current state
     * @param action Action taken
     * @param reward Reward received
     * @param nextState Next state after action
     */
    public Experience(double[] state, int action, double reward, double[] nextState) {
        // Create deep copies to prevent external modification
        this.state = Arrays.copyOf(state, state.length);
        this.action = action;
        this.reward = reward;
        this.nextState = Arrays.copyOf(nextState, nextState.length);
    }
    
    /**
     * Get the state
     * @return State
     */
    public double[] getState() {
        return Arrays.copyOf(state, state.length);
    }
    
    /**
     * Get the action
     * @return Action
     */
    public int getAction() {
        return action;
    }
    
    /**
     * Get the reward
     * @return Reward
     */
    public double getReward() {
        return reward;
    }
    
    /**
     * Get the next state
     * @return Next state
     */
    public double[] getNextState() {
        return Arrays.copyOf(nextState, nextState.length);
    }
    
    @Override
    public String toString() {
        return "Experience{" +
                "state=" + Arrays.toString(state) +
                ", action=" + action +
                ", reward=" + reward +
                ", nextState=" + Arrays.toString(nextState) +
                '}';
    }
}
