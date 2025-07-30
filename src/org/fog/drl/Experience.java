package org.fog.drl;

/**
 * Represents a reinforcement learning experience tuple (state, action, reward, next state)
 * Used for experience replay in DRL
 */
public class Experience {
    private double[] state;
    private int action;
    private double reward;
    private double[] nextState;
    private boolean terminal;
    
    /**
     * Constructor for an experience tuple
     * @param state Current state feature vector
     * @param action Selected action (device index)
     * @param reward Reward received
     * @param nextState Next state feature vector
     * @param terminal Whether this is a terminal state
     */
    public Experience(double[] state, int action, double reward, double[] nextState, boolean terminal) {
        this.state = state.clone();
        this.action = action;
        this.reward = reward;
        this.nextState = nextState.clone();
        this.terminal = terminal;
    }

    public double[] getState() {
        return state;
    }

    public int getAction() {
        return action;
    }

    public double getReward() {
        return reward;
    }

    public double[] getNextState() {
        return nextState;
    }
    
    public boolean isTerminal() {
        return terminal;
    }
    
    @Override
    public String toString() {
        return "Experience{action=" + action + ", reward=" + reward + ", terminal=" + terminal + "}";
    }
}
