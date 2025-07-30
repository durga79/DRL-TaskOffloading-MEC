package org.fog.drl;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Experience Replay Buffer for Deep Reinforcement Learning
 * Stores and samples experiences to break correlation between consecutive samples
 * and improve training stability
 */
public class ReplayBuffer {
    
    // Buffer to store experiences
    private List<Experience> buffer;
    
    // Maximum buffer size
    private int capacity;
    
    // Random number generator for sampling
    private Random random;
    
    /**
     * Constructor
     * @param capacity Maximum buffer size
     */
    public ReplayBuffer(int capacity) {
        this.buffer = new ArrayList<>();
        this.capacity = capacity;
        this.random = new Random();
    }
    
    /**
     * Add an experience to the buffer
     * @param experience Experience to add
     */
    public void add(Experience experience) {
        if (buffer.size() >= capacity) {
            // Remove oldest experience if buffer is full
            buffer.remove(0);
        }
        buffer.add(experience);
    }
    
    /**
     * Sample a mini-batch of experiences from the buffer
     * @param batchSize Size of mini-batch to sample
     * @return List of sampled experiences
     */
    public List<Experience> sample(int batchSize) {
        List<Experience> batch = new ArrayList<>();
        
        if (buffer.size() <= batchSize) {
            // Return all experiences if buffer contains fewer than batch size
            return new ArrayList<>(buffer);
        }
        
        // Randomly sample batch size experiences
        for (int i = 0; i < batchSize; i++) {
            int index = random.nextInt(buffer.size());
            batch.add(buffer.get(index));
        }
        
        return batch;
    }
    
    /**
     * Get the current size of the buffer
     * @return Buffer size
     */
    public int size() {
        return buffer.size();
    }
    
    /**
     * Clear the buffer
     */
    public void clear() {
        buffer.clear();
    }
}
