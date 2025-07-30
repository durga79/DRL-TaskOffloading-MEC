package org.fog.drl;

import java.util.Random;
import java.util.Arrays;

/**
 * A simple feed-forward neural network with one hidden layer
 * Used for approximating Q-values in the DRL agent
 */
public class NeuralNetwork {
    private int inputSize;
    private int hiddenSize;
    private int outputSize;
    
    // Network parameters (weights and biases)
    private double[][] weightsInputHidden;
    private double[] biasesHidden;
    private double[][] weightsHiddenOutput;
    private double[] biasesOutput;
    
    // Learning parameters
    private double learningRate = 0.001;
    private Random random;
    
    public NeuralNetwork(int inputSize, int hiddenSize, int outputSize) {
        this.inputSize = inputSize;
        this.hiddenSize = hiddenSize;
        this.outputSize = outputSize;
        this.random = new Random();
        
        // Initialize weights and biases with small random values
        initializeParameters();
    }
    
    private void initializeParameters() {
        // Initialize weights with Xavier/Glorot initialization
        double inputScale = Math.sqrt(2.0 / (inputSize + hiddenSize));
        double hiddenScale = Math.sqrt(2.0 / (hiddenSize + outputSize));
        
        weightsInputHidden = new double[inputSize][hiddenSize];
        biasesHidden = new double[hiddenSize];
        
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                weightsInputHidden[i][j] = (random.nextDouble() * 2 - 1) * inputScale;
            }
        }
        
        for (int i = 0; i < hiddenSize; i++) {
            biasesHidden[i] = 0; // Initialize biases to zero
        }
        
        weightsHiddenOutput = new double[hiddenSize][outputSize];
        biasesOutput = new double[outputSize];
        
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < outputSize; j++) {
                weightsHiddenOutput[i][j] = (random.nextDouble() * 2 - 1) * hiddenScale;
            }
        }
        
        for (int i = 0; i < outputSize; i++) {
            biasesOutput[i] = 0; // Initialize biases to zero
        }
    }
    
    /**
     * Forward pass through the neural network
     * @param input The input features
     * @return The predicted output values
     */
    public double[] forward(double[] input) {
        if (input.length != inputSize) {
            throw new IllegalArgumentException("Input size mismatch: expected " + inputSize + ", got " + input.length);
        }
        
        // Hidden layer with ReLU activation
        double[] hiddenActivations = new double[hiddenSize];
        for (int j = 0; j < hiddenSize; j++) {
            double sum = biasesHidden[j];
            for (int i = 0; i < inputSize; i++) {
                sum += input[i] * weightsInputHidden[i][j];
            }
            // ReLU activation
            hiddenActivations[j] = Math.max(0, sum);
        }
        
        // Output layer (linear activation)
        double[] output = new double[outputSize];
        for (int j = 0; j < outputSize; j++) {
            double sum = biasesOutput[j];
            for (int i = 0; i < hiddenSize; i++) {
                sum += hiddenActivations[i] * weightsHiddenOutput[i][j];
            }
            output[j] = sum;
        }
        
        return output;
    }
    
    /**
     * Train the network using backpropagation with a single sample
     * @param input Input features
     * @param target Target values
     * @return The loss value
     */
    public double train(double[] input, double[] target) {
        // Forward pass
        double[] hiddenActivations = new double[hiddenSize];
        boolean[] hiddenMask = new boolean[hiddenSize]; // To track which neurons were activated (for ReLU derivative)
        
        // Hidden layer
        for (int j = 0; j < hiddenSize; j++) {
            double sum = biasesHidden[j];
            for (int i = 0; i < inputSize; i++) {
                sum += input[i] * weightsInputHidden[i][j];
            }
            // ReLU activation
            hiddenActivations[j] = Math.max(0, sum);
            hiddenMask[j] = sum > 0; // true if the neuron was activated
        }
        
        // Output layer
        double[] output = new double[outputSize];
        for (int j = 0; j < outputSize; j++) {
            double sum = biasesOutput[j];
            for (int i = 0; i < hiddenSize; i++) {
                sum += hiddenActivations[i] * weightsHiddenOutput[i][j];
            }
            output[j] = sum; // Linear activation
        }
        
        // Calculate loss (Mean Squared Error)
        double loss = 0;
        for (int i = 0; i < outputSize; i++) {
            double error = target[i] - output[i];
            loss += error * error;
        }
        loss /= outputSize;
        
        // Backpropagation
        // Output layer gradients
        double[] outputGradients = new double[outputSize];
        for (int i = 0; i < outputSize; i++) {
            outputGradients[i] = 2 * (output[i] - target[i]) / outputSize; // MSE derivative
        }
        
        // Hidden layer gradients
        double[] hiddenGradients = new double[hiddenSize];
        for (int i = 0; i < hiddenSize; i++) {
            if (hiddenMask[i]) { // ReLU derivative is 1 for activated neurons, 0 otherwise
                double sum = 0;
                for (int j = 0; j < outputSize; j++) {
                    sum += outputGradients[j] * weightsHiddenOutput[i][j];
                }
                hiddenGradients[i] = sum;
            } else {
                hiddenGradients[i] = 0;
            }
        }
        
        // Update weights and biases
        // Output layer
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < outputSize; j++) {
                weightsHiddenOutput[i][j] -= learningRate * outputGradients[j] * hiddenActivations[i];
            }
        }
        
        for (int j = 0; j < outputSize; j++) {
            biasesOutput[j] -= learningRate * outputGradients[j];
        }
        
        // Hidden layer
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                weightsInputHidden[i][j] -= learningRate * hiddenGradients[j] * input[i];
            }
        }
        
        for (int j = 0; j < hiddenSize; j++) {
            biasesHidden[j] -= learningRate * hiddenGradients[j];
        }
        
        return loss;
    }
    
    /**
     * Copy parameters from another network
     * @param source The source network to copy from
     */
    public void copyParameters(NeuralNetwork source) {
        if (this.inputSize != source.inputSize || this.hiddenSize != source.hiddenSize || 
            this.outputSize != source.outputSize) {
            throw new IllegalArgumentException("Network architectures do not match for parameter copying");
        }
        
        // Copy input-hidden weights
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < hiddenSize; j++) {
                this.weightsInputHidden[i][j] = source.weightsInputHidden[i][j];
            }
        }
        
        // Copy hidden biases
        for (int i = 0; i < hiddenSize; i++) {
            this.biasesHidden[i] = source.biasesHidden[i];
        }
        
        // Copy hidden-output weights
        for (int i = 0; i < hiddenSize; i++) {
            for (int j = 0; j < outputSize; j++) {
                this.weightsHiddenOutput[i][j] = source.weightsHiddenOutput[i][j];
            }
        }
        
        // Copy output biases
        for (int i = 0; i < outputSize; i++) {
            this.biasesOutput[i] = source.biasesOutput[i];
        }
    }
    
    /**
     * Train the network using backpropagation with a batch of samples
     * @param inputs Batch of input features
     * @param targets Batch of target values
     * @param batchLearningRate Learning rate for batch training
     * @return Average loss across the batch
     */
    public double train(double[][] inputs, double[][] targets, double batchLearningRate) {
        if (inputs.length != targets.length) {
            throw new IllegalArgumentException("Input and target batch sizes do not match");
        }
        
        double totalLoss = 0;
        double originalLearningRate = this.learningRate;
        
        // Set the batch learning rate
        this.learningRate = batchLearningRate;
        
        // Train on each sample
        for (int i = 0; i < inputs.length; i++) {
            totalLoss += train(inputs[i], targets[i]);
        }
        
        // Restore original learning rate
        this.learningRate = originalLearningRate;
        
        // Return average loss
        return totalLoss / inputs.length;
    }
    
    /**
     * Predict output for an input vector
     * @param input Input features
     * @return Predicted output values
     */
    public double[] predict(double[] input) {
        return forward(input);
    }
    
    public void setLearningRate(double learningRate) {
        this.learningRate = learningRate;
    }
    
    public int getInputSize() {
        return inputSize;
    }
    
    public int getHiddenSize() {
        return hiddenSize;
    }
    
    public int getOutputSize() {
        return outputSize;
    }
    
    @Override
    public String toString() {
        return "NeuralNetwork{" +
                "inputSize=" + inputSize +
                ", hiddenSize=" + hiddenSize +
                ", outputSize=" + outputSize +
                '}';
    }
}
