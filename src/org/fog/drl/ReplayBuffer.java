package org.fog.drl;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Experience replay buffer for DRL training
 * Stores experience tuples and provides random sampling for batch training
 */
public class ReplayBuffer {
    private List<Experience> buffer;
    private int capacity;
    private Random random;
    
    /**
     * Constructor for replay buffer
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
     * Randomly sample a batch of experiences from the buffer
     * @param batchSize Number of experiences to sample
     * @return List of sampled experiences
     */
    public List<Experience> sample(int batchSize) {
        List<Experience> batch = new ArrayList<>(batchSize);
        
        if (buffer.size() <= batchSize) {
            // If buffer has fewer elements than batch size, return all
            return new ArrayList<>(buffer);
        }
        
        // Random sampling with replacement
        for (int i = 0; i < batchSize; i++) {
            int index = random.nextInt(buffer.size());
            batch.add(buffer.get(index));
        }
        
        return batch;
    }
    
    /**
     * Get the current size of the buffer
     * @return Current buffer size
     */
    public int size() {
        return buffer.size();
    }
    
    /**
     * Check if the buffer is empty
     * @return True if empty, false otherwise
     */
    public boolean isEmpty() {
        return buffer.isEmpty();
    }
    
    /**
     * Clear the buffer
     */
    public void clear() {
        buffer.clear();
    }
}
