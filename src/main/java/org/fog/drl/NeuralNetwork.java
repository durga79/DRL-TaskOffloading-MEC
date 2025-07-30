package org.fog.drl;

import java.util.Arrays;
import java.util.Random;

/**
 * Simple Neural Network implementation for Deep Q-Learning
 * This is a simplified implementation of a neural network for DQN
 * In a production environment, you would use a library like DL4J or TensorFlow
 */
public class NeuralNetwork {
    
    // Network architecture
    private int inputSize;
    private int outputSize;
    private int hiddenSize;
    
    // Network parameters
    private double[][] weightsInputHidden;
    private double[] biasHidden;
    private double[][] weightsHiddenOutput;
    private double[] biasOutput;
    
    // Hyperparameters
    private double learningRate;
    private Random random;
    
    /**
     * Constructor
     * @param inputSize Input layer size
     * @param outputSize Output layer size
     * @param learningRate Learning rate for optimization
     */
    public NeuralNetwork(int inputSize, int outputSize, double learningRate) {
        this.inputSize = inputSize;
        this.outputSize = outputSize;
        this.hiddenSize = 64; // Fixed hidden layer size
        this.learningRate = learningRate;
        this.random = new Random();
        
        // Initialize weights and biases
        initializeParameters();
    }
    
    /**
     * Initialize network parameters with random values
     */
    private void initializeParameters() {
        // Initialize weights with Xavier/Glorot initialization
        double inputScale = Math.sqrt(2.0 / (inputSize + hiddenSize));
        double hiddenScale = Math.sqrt(2.0 / (hiddenSize + outputSize));
        
        weightsInputHidden = new double[inputSize][hiddenSize];
        biasHidden = new double[hiddenSize];
        weightsHiddenOutput = new double[hiddenSize][outputSize];
        biasOutput = new double[outputSize];
        
        // Initialize input to hidden weights
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                weightsInputHidden[i][j] = (random.nextDouble() * 2 - 1) * inputScale;
            }
        }
        
        // Initialize hidden biases
        for (int i = 0; i < hiddenSize; i++) {
            biasHidden[i] = (random.nextDouble() * 2 - 1) * inputScale;
        }
        
        // Initialize hidden to output weights
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < outputSize; j++) {
                weightsHiddenOutput[i][j] = (random.nextDouble() * 2 - 1) * hiddenScale;
            }
        }
        
        // Initialize output biases
        for (int i = 0; i < outputSize; i++) {
            biasOutput[i] = (random.nextDouble() * 2 - 1) * hiddenScale;
        }
    }
    
    /**
     * Forward pass through the network
     * @param input Input vector
     * @return Output vector
     */
    public double[] predict(double[] input) {
        // Compute hidden layer activation
        double[] hiddenActivation = new double[hiddenSize];
        for (int i = 0; i < hiddenSize; i++) {
            hiddenActivation[i] = biasHidden[i];
            for (int j = 0; j < inputSize; j++) {
                hiddenActivation[i] += input[j] * weightsInputHidden[j][i];
            }
            // ReLU activation
            hiddenActivation[i] = Math.max(0, hiddenActivation[i]);
        }
        
        // Compute output layer
        double[] output = new double[outputSize];
        for (int i = 0; i < outputSize; i++) {
            output[i] = biasOutput[i];
            for (int j = 0; j < hiddenSize; j++) {
                output[i] += hiddenActivation[j] * weightsHiddenOutput[j][i];
            }
        }
        
        return output;
    }
    
    /**
     * Train the network using backpropagation
     * @param input Input vector
     * @param target Target output vector
     */
    public void train(double[] input, double[] target) {
        // Forward pass
        double[] hiddenActivation = new double[hiddenSize];
        for (int i = 0; i < hiddenSize; i++) {
            hiddenActivation[i] = biasHidden[i];
            for (int j = 0; j < inputSize; j++) {
                hiddenActivation[i] += input[j] * weightsInputHidden[j][i];
            }
            // ReLU activation
            hiddenActivation[i] = Math.max(0, hiddenActivation[i]);
        }
        
        double[] output = new double[outputSize];
        for (int i = 0; i < outputSize; i++) {
            output[i] = biasOutput[i];
            for (int j = 0; j < hiddenSize; j++) {
                output[i] += hiddenActivation[j] * weightsHiddenOutput[j][i];
            }
        }
        
        // Backpropagation
        
        // Output layer error
        double[] outputError = new double[outputSize];
        for (int i = 0; i < outputSize; i++) {
            outputError[i] = target[i] - output[i];
        }
        
        // Hidden layer error
        double[] hiddenError = new double[hiddenSize];
        for (int i = 0; i < hiddenSize; i++) {
            hiddenError[i] = 0;
            for (int j = 0; j < outputSize; j++) {
                hiddenError[i] += outputError[j] * weightsHiddenOutput[i][j];
            }
            // ReLU derivative
            hiddenError[i] = hiddenError[i] * (hiddenActivation[i] > 0 ? 1 : 0);
        }
        
        // Update weights and biases
        
        // Update output layer weights and biases
        for (int i = 0; i < outputSize; i++) {
            biasOutput[i] += learningRate * outputError[i];
            for (int j = 0; j < hiddenSize; j++) {
                weightsHiddenOutput[j][i] += learningRate * outputError[i] * hiddenActivation[j];
            }
        }
        
        // Update hidden layer weights and biases
        for (int i = 0; i < hiddenSize; i++) {
            biasHidden[i] += learningRate * hiddenError[i];
            for (int j = 0; j < inputSize; j++) {
                weightsInputHidden[j][i] += learningRate * hiddenError[i] * input[j];
            }
        }
    }
    
    /**
     * Copy weights from another network
     * @param sourceNetwork Source network
     */
    public void copyWeightsFrom(NeuralNetwork sourceNetwork) {
        // Copy input to hidden weights
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                this.weightsInputHidden[i][j] = sourceNetwork.weightsInputHidden[i][j];
            }
        }
        
        // Copy hidden biases
        for (int i = 0; i < hiddenSize; i++) {
            this.biasHidden[i] = sourceNetwork.biasHidden[i];
        }
        
        // Copy hidden to output weights
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < outputSize; j++) {
                this.weightsHiddenOutput[i][j] = sourceNetwork.weightsHiddenOutput[i][j];
            }
        }
        
        // Copy output biases
        for (int i = 0; i < outputSize; i++) {
            this.biasOutput[i] = sourceNetwork.biasOutput[i];
        }
    }
}
