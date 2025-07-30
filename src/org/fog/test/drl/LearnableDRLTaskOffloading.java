package org.fog.test.drl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.AppModule;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.drl.DRLTaskOffloadingAgent;
import org.fog.drl.Experience;
import org.fog.drl.NeuralNetwork;
import org.fog.drl.ReplayBuffer;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.*;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * Simulation of learning-based DRL Task Offloading in Mobile Edge Computing
 * Compatible with iFogSim API
 */
public class LearnableDRLTaskOffloading {
    
    private static boolean TRACE_FLAG = false;  // Set to false to disable CloudSim logging
    
    // DRL learning parameters
    private static double LEARNING_RATE = 0.001;
    private static double DISCOUNT_FACTOR = 0.95;
    private static double EPSILON_INITIAL = 1.0;
    private static double EPSILON_MIN = 0.1;
    private static double EPSILON_DECAY = 0.995;
    private static int REPLAY_BUFFER_SIZE = 1000;
    private static int BATCH_SIZE = 32;
    private static int HIDDEN_LAYER_SIZE = 24;
    
    // Simulation parameters
    private static int numOfDepts = 2;
    private static int numOfMobilesPerDept = 3;
    private static double EEG_TRANSMISSION_TIME = 10;
    
    // Learning components
    private static ReplayBuffer replayBuffer;
    private static NeuralNetwork qNetwork;
    private static NeuralNetwork targetNetwork;
    private static double epsilon = EPSILON_INITIAL;
    private static int trainingSteps = 0;
    
    // Environment state
    private static List<FogDevice> fogDevices = new ArrayList<>();
    private static List<Sensor> sensors = new ArrayList<>();
    private static List<Actuator> actuators = new ArrayList<>();
    private static Map<String, Integer> deviceToIndex = new HashMap<>();
    private static Map<String, Integer> moduleToIndex = new HashMap<>();
    private static List<String> deviceNames = new ArrayList<>();
    private static List<String> moduleNames = new ArrayList<>();
    
    // Metrics tracking
    private static Map<String, Double> deviceEnergyMap = new HashMap<>();
    private static Map<String, Double> moduleExecutionTimeMap = new HashMap<>();
    private static Map<String, Map<String, Double>> moduleDeviceQValues = new HashMap<>();
    
    public static void main(String[] args) {
        try {
            // Disable CloudSim logs more thoroughly
            Log.disable();
            java.util.logging.Logger.getLogger("org.cloudbus").setLevel(java.util.logging.Level.OFF);
            System.setProperty("org.cloudsim.cloudsim.core.CloudSim.PRINT", "false");
            
            // Initialize CloudSim with logging disabled
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            CloudSim.init(num_user, calendar, false);
            
            // Create the fog broker
            FogBroker broker = new FogBroker("broker");
            
            // Create fog devices
            createFogDevices(broker.getId());
            
            // Create application
            Application application = createApplication(broker.getId(), "drl_app");
            application.setUserId(broker.getId());
            
            // Initialize DRL components
            initializeDRL(application);
            
            // Create initial module mapping - could be random or fixed for first episode
            ModuleMapping moduleMapping = createInitialModuleMapping();
            
            // Create sensors and actuators
            createSensorsAndActuators(broker.getId(), application.getAppId());
            
            // Create controller
            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);
            controller.submitApplication(application, new ModulePlacementMapping(fogDevices, application, moduleMapping));
            
            // Start simulation
            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            CloudSim.startSimulation();
            
            // Collect results and update DRL model
            updateDRLModel();
            
            // Stop simulation
            CloudSim.stopSimulation();
            Log.printLine("Learning-based DRL Task Offloading simulation finished!");
            
            // Print results
            printResults();
            
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }
    
    /**
     * Initialize DRL components
     */
    private static void initializeDRL(Application application) {
        // Map devices and modules to indices
        int deviceIndex = 0;
        for (FogDevice device : fogDevices) {
            String deviceName = device.getName();
            deviceNames.add(deviceName);
            deviceToIndex.put(deviceName, deviceIndex++);
        }
        
        int moduleIndex = 0;
        for (AppModule module : application.getModules()) {
            String moduleName = module.getName();
            moduleNames.add(moduleName);
            moduleToIndex.put(moduleName, moduleIndex++);
        }
        
        // Calculate state and action dimensions
        int stateSize = deviceNames.size() + moduleNames.size(); // Simple state representation
        int actionSize = deviceNames.size();  // One action per device
        
        // Initialize neural networks for DRL
        qNetwork = new NeuralNetwork(stateSize, HIDDEN_LAYER_SIZE, actionSize);
        targetNetwork = new NeuralNetwork(stateSize, HIDDEN_LAYER_SIZE, actionSize);
        copyNetworkParameters();
        
        // Initialize replay buffer
        replayBuffer = new ReplayBuffer(REPLAY_BUFFER_SIZE);
        
        // Initialize Q-value tracking
        for (String moduleName : moduleNames) {
            Map<String, Double> deviceValues = new HashMap<>();
            for (String deviceName : deviceNames) {
                // Initialize with small random values
                deviceValues.put(deviceName, Math.random() * 0.1);
            }
            moduleDeviceQValues.put(moduleName, deviceValues);
        }
    }
    
    /**
     * Copy parameters from Q-network to target network
     */
    private static void copyNetworkParameters() {
        targetNetwork.copyParameters(qNetwork);
    }
    
    /**
     * Update DRL model based on simulation results
     */
    private static void updateDRLModel() {
        if (replayBuffer.size() < BATCH_SIZE) {
            Log.printLine("Not enough experiences for training yet.");
            return;
        }
        
        // Sample a batch from replay buffer
        List<Experience> batch = replayBuffer.sample(BATCH_SIZE);
        
        double[][] states = new double[BATCH_SIZE][];
        double[][] targets = new double[BATCH_SIZE][];
        
        // Prepare training data
        for (int i = 0; i < BATCH_SIZE; i++) {
            Experience exp = batch.get(i);
            states[i] = exp.getState();
            
            // Get current Q-values for the state
            double[] qValues = qNetwork.predict(exp.getState());
            
            if (exp.isTerminal()) {
                // For terminal state, target is just the reward
                qValues[exp.getAction()] = exp.getReward();
            } else {
                // For non-terminal states, target is reward + discount * max future Q-value
                double[] nextQValues = targetNetwork.predict(exp.getNextState());
                double maxQ = -Double.MAX_VALUE;
                for (double q : nextQValues) {
                    maxQ = Math.max(maxQ, q);
                }
                qValues[exp.getAction()] = exp.getReward() + DISCOUNT_FACTOR * maxQ;
            }
            
            targets[i] = qValues;
        }
        
        // Train the network
        double loss = qNetwork.train(states, targets, LEARNING_RATE);
        Log.printLine("DRL training - Loss: " + loss);
        
        // Update target network periodically
        trainingSteps++;
        if (trainingSteps % 10 == 0) {
            copyNetworkParameters();
            Log.printLine("Target network updated.");
        }
        
        // Decay epsilon
        if (epsilon > EPSILON_MIN) {
            epsilon *= EPSILON_DECAY;
            Log.printLine("Epsilon decayed to: " + epsilon);
        }
        
        // Update the tracked Q-values for visualization
        updateTrackedQValues();
    }
    
    /**
     * Update tracked Q-values for all module-device pairs
     */
    private static void updateTrackedQValues() {
        for (String moduleName : moduleNames) {
            int moduleIdx = moduleToIndex.get(moduleName);
            
            // Create a state vector for this module
            double[] state = createStateVector(moduleName);
            
            // Get Q-values for this state
            double[] qValues = qNetwork.predict(state);
            
            // Update the Q-value map
            Map<String, Double> deviceValues = moduleDeviceQValues.get(moduleName);
            for (int i = 0; i < deviceNames.size(); i++) {
                String deviceName = deviceNames.get(i);
                deviceValues.put(deviceName, qValues[i]);
            }
        }
    }
    
    /**
     * Create state vector for DRL agent
     * @param moduleName The module for which to create state
     * @return State vector
     */
    private static double[] createStateVector(String moduleName) {
        // Simple state representation:
        // - One-hot encoding of the module
        // - Device utilization
        
        double[] state = new double[moduleNames.size() + deviceNames.size()];
        
        // Module one-hot encoding
        int moduleIdx = moduleToIndex.get(moduleName);
        state[moduleIdx] = 1.0;
        
        // Device utilization (in a real implementation, this would be actual utilization)
        for (int i = 0; i < deviceNames.size(); i++) {
            String deviceName = deviceNames.get(i);
            FogDevice device = getFogDeviceByName(deviceName);
            
            if (device != null) {
                // Use a simple CPU utilization metric
                double utilization = device.getHost().getUtilizationMips();
                state[moduleNames.size() + i] = utilization;
            }
        }
        
        return state;
    }
    
    /**
     * Get fog device by name
     */
    private static FogDevice getFogDeviceByName(String name) {
        for (FogDevice device : fogDevices) {
            if (device.getName().equals(name)) {
                return device;
            }
        }
        return null;
    }
    
    /**
     * Select a device for a module using epsilon-greedy policy
     */
    private static String selectDeviceForModule(String moduleName) {
        // Epsilon-greedy action selection
        if (Math.random() < epsilon) {
            // Exploration: random device
            int randomIndex = (int)(Math.random() * deviceNames.size());
            return deviceNames.get(randomIndex);
        } else {
            // Exploitation: best Q-value
            Map<String, Double> deviceValues = moduleDeviceQValues.get(moduleName);
            String bestDevice = null;
            double bestValue = -Double.MAX_VALUE;
            
            for (Map.Entry<String, Double> entry : deviceValues.entrySet()) {
                if (entry.getValue() > bestValue) {
                    bestValue = entry.getValue();
                    bestDevice = entry.getKey();
                }
            }
            
            return bestDevice;
        }
    }
    
    /**
     * Create initial module mapping for first episode
     */
    private static ModuleMapping createInitialModuleMapping() {
        ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
        
        // For the first episode, use a sensible initial placement
        // client → mobile, object_detector → edge, motion_tracker → cloud
        
        // Or use the DRL policy if we have some learned values
        for (String moduleName : moduleNames) {
            String selectedDevice = selectDeviceForModule(moduleName);
            moduleMapping.addModuleToDevice(moduleName, selectedDevice);
            Log.printLine("Initial placement: " + moduleName + " → " + selectedDevice);
        }
        
        return moduleMapping;
    }
    
    /**
     * Create the application model
     */
    private static Application createApplication(int userId, String appId) {
        Application application = Application.createApplication(appId, userId);
        
        // Add AppModules
        application.addAppModule("client", 10);
        application.addAppModule("object_detector", 500);  
        application.addAppModule("motion_tracker", 1000);
        
        // Add AppEdges
        application.addAppEdge("SENSOR", "client", 1000, 500, "SENSOR", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("client", "object_detector", 1000, 500, "RAW_VIDEO", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("object_detector", "motion_tracker", 1000, 500, "DETECTED_OBJECTS", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("motion_tracker", "client", 1000, 500, "OBJECT_TRACKER", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("client", "ACTUATOR", 1000, 500, "ACTION_COMMANDS", Tuple.DOWN, AppEdge.ACTUATOR);
        
        // Add tupleMapping
        application.addTupleMapping("client", "SENSOR", "RAW_VIDEO", new FractionalSelectivity(1.0));
        application.addTupleMapping("object_detector", "RAW_VIDEO", "DETECTED_OBJECTS", new FractionalSelectivity(1.0));
        application.addTupleMapping("motion_tracker", "DETECTED_OBJECTS", "OBJECT_TRACKER", new FractionalSelectivity(1.0));
        application.addTupleMapping("client", "OBJECT_TRACKER", "ACTION_COMMANDS", new FractionalSelectivity(1.0));
        
        // Add application loops
        final AppLoop loop1 = new AppLoop(new ArrayList<String>() {{
            add("SENSOR");
            add("client");
            add("object_detector");
            add("motion_tracker");
            add("client");
            add("ACTUATOR");
        }});
        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop1);
        }};
        application.setLoops(loops);
        
        return application;
    }
    
    /**
     * Create sensors and actuators
     */
    private static void createSensorsAndActuators(int userId, String appId) {
        for (FogDevice mobile : fogDevices) {
            if (mobile.getName().startsWith("mobile-")) {
                Sensor sensor = new Sensor("sensor-" + mobile.getName(), "SENSOR", userId, appId, 
                                      new DeterministicDistribution(EEG_TRANSMISSION_TIME));
                sensor.setGatewayDeviceId(mobile.getId());
                sensor.setLatency(6.0);  // latency of connection between sensor and gateway is 6 ms
                sensors.add(sensor);
                
                Actuator actuator = new Actuator("actuator-" + mobile.getName(), userId, appId, "ACTUATOR");
                actuator.setGatewayDeviceId(mobile.getId());
                actuator.setLatency(1.0);  // latency of connection between gateway and actuator is 1 ms
                actuators.add(actuator);
            }
        }
    }
    
    /**
     * Create fog devices
     */
    private static void createFogDevices(int userId) {
        // Create cloud device
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25);
        cloud.setParentId(-1);
        fogDevices.add(cloud);
        
        // Create edge servers
        for(int i = 0; i < numOfDepts; i++) {
            FogDevice edge = createFogDevice("edge-server-"+i, 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
            edge.setParentId(cloud.getId());
            edge.setUplinkLatency(100); // latency of connection between edge server and cloud is 100 ms
            fogDevices.add(edge);
            
            // Create mobile devices
            for(int j = 0; j < numOfMobilesPerDept; j++) {
                FogDevice mobile = createFogDevice("mobile-"+i+"-"+j, 1000, 1000, 10000, 270, 2, 0, 87.53, 82.44);
                mobile.setParentId(edge.getId());
                mobile.setUplinkLatency(2); // latency of connection between mobile and edge is 2 ms
                fogDevices.add(mobile);
            }
        }
    }
    
    /**
     * Create a fog device
     */
    private static FogDevice createFogDevice(String nodeName, long mips, int ram, long upBw, long downBw, 
                                       int level, double ratePerMips, double busyPower, double idlePower) {
        List<Pe> peList = new ArrayList<Pe>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));
        
        int hostId = FogUtils.generateEntityId();
        long storage = 1000000; // host storage
        int bw = 10000;
        
        PowerHost host = new PowerHost(
            hostId,
            new RamProvisionerSimple(ram),
            new BwProvisionerOverbooking(bw),
            storage,
            peList,
            new StreamOperatorScheduler(peList),
            new FogLinearPowerModel(busyPower, idlePower)
        );
        
        List<Host> hostList = new ArrayList<Host>();
        hostList.add(host);
        
        String arch = "x86"; // system architecture
        String os = "Linux"; // operating system
        String vmm = "Xen";
        double time_zone = 10.0; // time zone this resource located
        double cost = 3.0; // the cost of using processing in this resource
        double costPerMem = 0.05; // the cost of using memory in this resource
        double costPerStorage = 0.001; // the cost of using storage in this resource
        double costPerBw = 0.0; // the cost of using bw in this resource
        LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN devices by now
        
        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
            arch, os, vmm, host, time_zone, cost, costPerMem, costPerStorage, costPerBw);
        
        FogDevice fogdevice = null;
        try {
            fogdevice = new FogDevice(nodeName, characteristics, 
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        fogdevice.setLevel(level);
        return fogdevice;
    }
    
    /**
     * Store a new experience in the replay buffer
     */
    public static void addExperience(String moduleName, String deviceName, double reward, boolean isTerminal) {
        // Create state vectors
        double[] state = createStateVector(moduleName);
        double[] nextState = createStateVector(moduleName); // In a full impl, this would be the actual next state
        
        // Get action index from device name
        int actionIndex = deviceToIndex.get(deviceName);
        
        // Add to replay buffer
        Experience exp = new Experience(state, actionIndex, reward, nextState, isTerminal);
        replayBuffer.add(exp);
        
        // Disable experience logging
        // Log.printLine("Added experience: module=" + moduleName + 
        //              ", device=" + deviceName + ", reward=" + reward +
        //              ", terminal=" + isTerminal);
    }
    
    /**
     * Print results of the simulation
     */
    private static void printResults() {
        // We'll use System.out directly for results without re-enabling CloudSim logs
        
        System.out.println("\n\n\nSimulation completed.");
        System.out.println("=========================================\n============== RESULTS ==================\n=========================================\n" +
                   "EXECUTION TIME : " + (Calendar.getInstance().getTimeInMillis() - TimeKeeper.getInstance().getSimulationStartTime()));
        System.out.println("=========================================");
        System.out.println("APPLICATION LOOP DELAYS");
        System.out.println("=========================================");
        System.out.println(TimeKeeper.getInstance().getLoopIdToTupleIds());
        System.out.println("=========================================");
        System.out.println("TUPLE CPU EXECUTION DELAY");
        System.out.println("=========================================");
        System.out.println(TimeKeeper.getInstance().getTupleCPUExecutionDelay());
        System.out.println("=========================================");
        
        // Print energy consumption for each device
        for (FogDevice fogDevice : fogDevices) {
            System.out.println(fogDevice.getName() + " : Energy Consumed = " + fogDevice.getEnergyConsumption());
        }
        
        System.out.println("Cost of execution in cloud = " + FogUtils.getCostOfExecution());
        System.out.println("Total network usage = " + FogUtils.getNetworkUsage());
    }
}
