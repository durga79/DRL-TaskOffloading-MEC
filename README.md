# Deep Reinforcement Learning for Task Offloading in Mobile Edge Computing

This project implements a proof-of-concept for the paper "Deep Reinforcement Learning for Task Offloading in Mobile Edge Computing" by Wang et al. (IEEE INFOCOM 2020). The implementation leverages iFogSim, a toolkit for modeling and simulation of IoT, Fog, and Edge computing environments.

## Project Structure

- `src/org/fog/drl`: Core DRL components for task offloading
  - `DRLTaskOffloadingAgent.java`: Implementation of the DRL agent using Deep Q-Network
  - `NeuralNetwork.java`: Custom neural network implementation for Q-value function approximation
  - `Experience.java`: Class representing state-action-reward-nextState-terminal tuples
  - `ReplayBuffer.java`: Experience replay buffer for DRL training
  - `DRLController.java`: Controller that manages DRL decision-making

- `src/org/fog/test/drl`: Simulation examples
  - `SimpleDRLTaskOffloading.java`: Basic implementation with hardcoded Q-values
  - `LearnableDRLTaskOffloading.java`: Enhanced implementation with actual DRL learning
  - `FullDRLTaskOffloading.java`: Comprehensive implementation with advanced features

- `classes/`: Compiled Java classes
- `iFogSim/`: The core iFogSim simulator with dependencies
  - `jars/`: Required libraries including CloudSim and Apache Commons
- `results/`: Storage for simulation outputs (see Results section)
  - `data/`: Raw result data files
  - `charts/`: Generated charts and visualizations
  - `metrics/`: Collected metrics and performance data

## Implementation Overview

This implementation focuses on using Deep Reinforcement Learning (DRL) to optimize task offloading decisions in Mobile Edge Computing (MEC) environments. The main components include:

1. **DRL Task Offloading Agent**: Makes decisions on where to offload computational tasks using an epsilon-greedy policy with neural network Q-value approximation.
2. **Environment Model**: Represents the MEC environment with edge devices, cloud servers, and network conditions.
3. **State Representation**: Captures the system state including device workloads, network conditions, and task requirements.
4. **Reward Function**: Balances latency, energy consumption, and resource utilization.
5. **DQN Implementation**: Deep Q-Network algorithm for reinforcement learning with experience replay.
6. **Neural Network**: Single hidden layer neural network for Q-value function approximation.
7. **Experience Replay**: Buffer storing experiences for batch training of the neural network.

### Available Simulation Models

1. **SimpleDRLTaskOffloading**:
   - A simplified implementation that uses a pre-defined Q-value table
   - Demonstrates basic task offloading concepts without neural network learning
   - Fully compatible with the iFogSim API and serves as an introductory example

2. **LearnableDRLTaskOffloading**:
   - Enhanced implementation with actual DRL learning components
   - Implements epsilon-greedy exploration, neural network training, and experience replay
   - Provides detailed metrics on execution time, energy consumption, and network usage

3. **FullDRLTaskOffloading**:
   - Comprehensive implementation with advanced features
   - Includes more sophisticated state representation and reward computation
   - Contains additional evaluation metrics for comparison with baseline approaches

## Setup and Installation

### Prerequisites

- Java Development Kit (JDK) 8 or higher
- Maven 3.5+ (if using Maven for dependency management)
- Git (for cloning the repository)

### Installation Steps

1. Clone the repository:
   ```bash
   git clone https://github.com/yourusername/DRL-TaskOffloading-MEC.git
   cd DRL-TaskOffloading-MEC
   ```

2. Compile the code:
   ```bash
   # Using javac directly
   javac -d classes -cp iFogSim/jars/*:iFogSim/jars/cloudsim-3.0.3.jar:iFogSim/jars/cloudsim-examples-3.0.3.jar:iFogSim/jars/commons-math3-3.5/commons-math3-3.5.jar src/org/fog/drl/*.java src/org/fog/test/drl/*.java
   
   # Or using Maven (if configured)
   mvn clean compile
   ```

### Required Dependencies

All required dependencies are included in the repository under the `iFogSim/jars/` directory:
- CloudSim 3.0.3
- Apache Commons Math 3.5
- Guava 18.0
- JSON Simple 1.1.1

## Running the Simulations

### Running with Minimal Output

To run the simulation with minimal output (showing only the final results):

```bash
# Execute the provided script
./run_simulation.sh

# Or run directly with Java
java -cp classes:iFogSim/jars/*:iFogSim/jars/cloudsim-3.0.3.jar:iFogSim/jars/cloudsim-examples-3.0.3.jar:iFogSim/jars/commons-math3-3.5/commons-math3-3.5.jar org.fog.test.drl.LearnableDRLTaskOffloading
```

### Available Simulation Classes

You can run different simulation models based on your requirements:

1. Simple DRL Task Offloading:
   ```bash
   java -cp classes:iFogSim/jars/*:iFogSim/jars/cloudsim-3.0.3.jar:iFogSim/jars/cloudsim-examples-3.0.3.jar:iFogSim/jars/commons-math3-3.5/commons-math3-3.5.jar org.fog.test.drl.SimpleDRLTaskOffloading
   ```

2. Learnable DRL Task Offloading:
   ```bash
   java -cp classes:iFogSim/jars/*:iFogSim/jars/cloudsim-3.0.3.jar:iFogSim/jars/cloudsim-examples-3.0.3.jar:iFogSim/jars/commons-math3-3.5/commons-math3-3.5.jar org.fog.test.drl.LearnableDRLTaskOffloading
   ```

3. Full DRL Task Offloading:
   ```bash
   java -cp classes:iFogSim/jars/*:iFogSim/jars/cloudsim-3.0.3.jar:iFogSim/jars/cloudsim-examples-3.0.3.jar:iFogSim/jars/commons-math3-3.5/commons-math3-3.5.jar org.fog.test.drl.FullDRLTaskOffloading
   ```

### Modifying Simulation Parameters

To modify simulation parameters such as the number of edge servers, mobile devices, or learning parameters, edit the corresponding variables in the simulation class files:

- For DRL parameters: Edit the variables at the beginning of `LearnableDRLTaskOffloading.java`
- For topology configuration: Modify the `createFogDevices()` method in the simulation classes

## Results and Output

All simulation results are stored in the `results/` directory with the following structure:

```
results/
├── data/         # Raw simulation data in CSV/JSON format
├── charts/       # Generated charts for visualization (PNG, PDF)
└── metrics/      # Performance metrics and comparisons
```

### Key Performance Metrics

The simulations track and report the following key metrics:

1. **Execution Time**: Total time required to complete all tasks
2. **Application Loop Delays**: Time taken for each application loop
3. **Tuple CPU Execution Delay**: Processing time for individual tuples
4. **Energy Consumption**: Energy used by each device in the system
5. **Cloud Execution Cost**: Cost associated with using cloud resources
6. **Network Usage**: Total amount of data transferred across the network

### Example Output

Below is an example of the simulation results output:

```
Simulation completed.
=========================================
============== RESULTS ==================
=========================================
EXECUTION TIME : 291
=========================================
APPLICATION LOOP DELAYS
=========================================
[SENSOR, client, object_detector, motion_tracker, client, ACTUATOR] ---> null
=========================================
TUPLE CPU EXECUTION DELAY
=========================================
SENSOR ---> 1.099999999999909
=========================================
cloud : Energy Consumed = 2664000.0
edge-server-0 : Energy Consumed = 166866.59999999995
mobile-0-0 : Energy Consumed = 166882.40600000016
mobile-0-1 : Energy Consumed = 175060.0
mobile-0-2 : Energy Consumed = 175060.0
edge-server-1 : Energy Consumed = 166866.59999999995
mobile-1-0 : Energy Consumed = 164880.0
mobile-1-1 : Energy Consumed = 164880.0
mobile-1-2 : Energy Consumed = 164880.0
Cost of execution in cloud = 0.0
Total network usage = 29682.0
```

### Generating Charts

To generate charts from simulation results:

```bash
# First run a simulation and collect results
java -cp classes:iFogSim/jars/*:iFogSim/jars/cloudsim-3.0.3.jar:iFogSim/jars/cloudsim-examples-3.0.3.jar:iFogSim/jars/commons-math3-3.5/commons-math3-3.5.jar org.fog.test.drl.LearnableDRLTaskOffloading > results/data/drl_results.txt

# Process the results to generate charts (if JFreeChart is available)
# java -cp classes:iFogSim/jars/*:iFogSim/jars/jfreechart-1.5.3.jar org.fog.utils.ResultsVisualizer results/data/drl_results.txt
```

## References

- Wang, J., et al. (2020). "Deep Reinforcement Learning for Task Offloading in Mobile Edge Computing." IEEE INFOCOM 2020.
- H. Gupta, A. V. Dastjerdi, S. K. Ghosh, and R. Buyya, "iFogSim: A toolkit for modeling and simulation of resource management techniques in the Internet of Things, Edge and Fog computing environments," Software: Practice and Experience, vol. 47, no. 9, pp. 1275-1296, 2017.

## Contributing

Contributions to this project are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Submit a pull request

## License

This project is licensed under the terms of the MIT license.
# DRL-TaskOffloading-MEC
