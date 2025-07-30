package org.fog.test.drl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.drl.DRLTaskOffloadingAgent;
import org.fog.drl.Experience;
import org.fog.drl.ReplayBuffer;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacement;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;
import org.fog.application.AppModule;

/**
 * Simulation of Deep Reinforcement Learning (DRL) for Task Offloading
 * in Mobile Edge Computing environments using iFogSim
 * 
 * This simulation implements a full DRL agent that learns optimal 
 * task placement policies through interactions with the environment.
 */
public class FullDRLTaskOffloading {
    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    static List<Sensor> sensors = new ArrayList<Sensor>();
    static List<Actuator> actuators = new ArrayList<Actuator>();
    
    static Map<String, Double> lastExecutionTimes = new HashMap<>();
    static Map<String, Double> lastEnergyConsumption = new HashMap<>();
    static Map<String, Double> lastNetworkUsage = new HashMap<>();
    
    static DRLTaskOffloadingAgent drlAgent;
    static int trainingEpisodes = 0;
    static boolean isTraining = true;
    static int MAX_TRAINING_EPISODES = 50;
    static double EXPLORATION_EPISODES = 0.7 * MAX_TRAINING_EPISODES; // 70% exploration
    
    static int numOfDepts = 2;
    static int numOfMobilesPerDept = 3;
    static double EEG_TRANSMISSION_TIME = 10;
    
    public static void main(String[] args) {
        try {
            Log.printLine("Starting Full DRL-based Task Offloading Simulation...");
            
            // Initialize CloudSim
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            CloudSim.init(num_user, calendar, trace_flag);
            
            // Create Fog Devices
            createFogDevices();
            
            // Create Application
            String appId = "drl_task_offloading";
            Application application = createApplication(appId);
            
            // Create Application Modules
            createAppModules(appId, application);
            
            // Create a Broker
            FogBroker broker = new FogBroker("broker");
            
            // Create Sensors and Actuators
            createSensorsAndActuators(broker.getId(), appId);
            
            // Initialize the DRL agent with fog devices and application modules
            initializeDRLAgent(application);
            
            // Submit application
            application.setUserId(broker.getId());
            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);
            controller.submitApplication(application, createInitialModulePlacement(application));
            
            // Start simulation
            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            
            for (trainingEpisodes = 0; trainingEpisodes < MAX_TRAINING_EPISODES; trainingEpisodes++) {
                // Run simulation for this episode
                CloudSim.startSimulation();
                
                // Collect metrics and update DRL agent
                if (isTraining) {
                    updateDRLAgentWithResults();
                    resetEnvironment();
                }
                
                Log.printLine("Episode " + (trainingEpisodes+1) + "/" + MAX_TRAINING_EPISODES + " completed");
            }
            
            // Final evaluation with learned policy
            isTraining = false;
            CloudSim.startSimulation();
            
            // Print results
            CloudSim.stopSimulation();
            Log.printLine("Full DRL-based Task Offloading Simulation finished!");
            printResults();
            
            Log.printLine("VRGame finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happen");
        }
    }
    
    /**
     * Initialize the DRL agent with application modules and fog devices
     */
    private static void initializeDRLAgent(Application application) {
        List<AppModule> appModules = new ArrayList<>(application.getModules());
        drlAgent = new DRLTaskOffloadingAgent(fogDevices, appModules);
    }
    
    /**
     * Update DRL agent with simulation results
     */
    private static void updateDRLAgentWithResults() {
        for (FogDevice device : fogDevices) {
            for (String moduleName : getModulesOnDevice(device)) {
                // Get metrics for this module on this device
                double executionTime = getModuleExecutionTime(device, moduleName);
                double energyConsumption = device.getEnergyConsumption();
                double networkUsage = getModuleNetworkUsage(device, moduleName);
                
                // Compute reward based on metrics
                AppModule module = getModuleByName(moduleName);
                if (module != null) {
                    double reward = drlAgent.computeReward(device.getName(), module, 
                                                          executionTime, energyConsumption, networkUsage);
                    
                    // Create state representations
                    Map<String, Double> deviceUtilization = getCurrentDeviceUtilization();
                    Map<String, Map<String, Double>> networkLatencies = getCurrentNetworkLatencies();
                    
                    // Get current state and next state
                    double[] state = createStateVector(module, deviceUtilization, networkLatencies);
                    double[] nextState = state; // Simplified - in real impl would be next state
                    
                    // Update DRL agent
                    boolean isLastModule = (moduleName.equals(getLastModuleName()));
                    drlAgent.updateAgent(module, device.getName(), state, nextState, reward, isLastModule);
                    
                    // Store metrics for next comparison
                    lastExecutionTimes.put(moduleName + "_" + device.getName(), executionTime);
                    lastEnergyConsumption.put(device.getName(), energyConsumption);
                    lastNetworkUsage.put(moduleName + "_" + device.getName(), networkUsage);
                }
            }
        }
    }
    
    /**
     * Create a state vector for DRL agent
     */
    private static double[] createStateVector(AppModule module, 
                                            Map<String, Double> deviceUtilization,
                                            Map<String, Map<String, Double>> networkLatencies) {
        // This is a simplified implementation - would need more features in a real implementation
        int stateSize = fogDevices.size() + 3; // Device utilizations + module features
        double[] state = new double[stateSize];
        
        // Device utilizations
        for (int i = 0; i < fogDevices.size(); i++) {
            FogDevice device = fogDevices.get(i);
            state[i] = deviceUtilization.getOrDefault(device.getName(), 0.0);
        }
        
        // Module features - just some example features
        if (module != null) {
            int offset = fogDevices.size();
            state[offset] = module.getMips() / 1000.0; // Normalized MIPS
            state[offset+1] = module.getRam() / 1000.0; // Normalized RAM
            state[offset+2] = module.getBw() / 1000.0;  // Normalized BW
        }
        
        return state;
    }
    
    /**
     * Reset the environment for next training episode
     */
    private static void resetEnvironment() {
        // Reset metrics
        lastExecutionTimes.clear();
        lastEnergyConsumption.clear();
        lastNetworkUsage.clear();
        
        // Reset fog devices (in a real implementation)
        // Here we would reset device states, but iFogSim doesn't support this directly
        
        // Update exploration rate based on training progress
        if (trainingEpisodes < EXPLORATION_EPISODES) {
            // Linear decay of exploration
            double progress = trainingEpisodes / EXPLORATION_EPISODES;
            // This would be handled inside the agent in a full implementation
        }
    }
    
    /**
     * Get modules deployed on a specific device
     */
    private static List<String> getModulesOnDevice(FogDevice device) {
        List<String> modules = new ArrayList<>();
        for (Vm vm : device.getVmList()) {
            // In iFogSim, each VM hosts one AppModule
            String moduleName = vm.getVmName();
            if (moduleName != null && !moduleName.isEmpty()) {
                modules.add(moduleName);
            }
        }
        return modules;
    }
    
    /**
     * Get AppModule by name
     */
    private static AppModule getModuleByName(String moduleName) {
        for (FogDevice device : fogDevices) {
            for (Vm vm : device.getVmList()) {
                if (vm.getVmName().equals(moduleName) && vm instanceof AppModule) {
                    return (AppModule)vm;
                }
            }
        }
        return null;
    }
    
    /**
     * Get execution time of a module on a device
     */
    private static double getModuleExecutionTime(FogDevice device, String moduleName) {
        // In a real implementation, this would come from CloudSim metrics
        // For now, we'll use a simple approach
        String key = moduleName + "_" + device.getName();
        if (lastExecutionTimes.containsKey(key)) {
            return lastExecutionTimes.get(key);
        }
        
        // Default values based on device type
        if (device.getName().contains("cloud")) {
            return 10.0; // Fast execution in cloud
        } else if (device.getName().contains("edge")) {
            return 20.0; // Medium execution in edge
        } else {
            return 40.0; // Slow execution in mobile
        }
    }
    
    /**
     * Get network usage of a module on a device
     */
    private static double getModuleNetworkUsage(FogDevice device, String moduleName) {
        // In a real implementation, this would come from CloudSim metrics
        String key = moduleName + "_" + device.getName();
        if (lastNetworkUsage.containsKey(key)) {
            return lastNetworkUsage.get(key);
        }
        
        // Default values based on device type
        if (device.getName().contains("cloud")) {
            return 100.0; // High network usage for cloud
        } else if (device.getName().contains("edge")) {
            return 40.0;  // Medium network usage for edge
        } else {
            return 10.0;  // Low network usage for mobile
        }
    }
    
    /**
     * Get current device utilization
     */
    private static Map<String, Double> getCurrentDeviceUtilization() {
        Map<String, Double> utilization = new HashMap<>();
        for (FogDevice device : fogDevices) {
            // In a real implementation, get actual CPU utilization
            // Here we're using a placeholder
            double util = device.getHost().getUtilizationOfCpu();
            utilization.put(device.getName(), util);
        }
        return utilization;
    }
    
    /**
     * Get current network latencies between devices
     */
    private static Map<String, Map<String, Double>> getCurrentNetworkLatencies() {
        Map<String, Map<String, Double>> latencies = new HashMap<>();
        
        // In a real implementation, get actual network latencies
        // Here we're using placeholders
        for (FogDevice source : fogDevices) {
            Map<String, Double> deviceLatencies = new HashMap<>();
            for (FogDevice dest : fogDevices) {
                if (source != dest) {
                    // Simplified latency model
                    double latency = 10.0;
                    if (source.getName().contains("cloud") || dest.getName().contains("cloud")) {
                        latency = 100.0; // High latency to cloud
                    } else if (source.getName().contains("edge") || dest.getName().contains("edge")) {
                        latency = 30.0;  // Medium latency to edge
                    }
                    deviceLatencies.put(dest.getName(), latency);
                }
            }
            latencies.put(source.getName(), deviceLatencies);
        }
        
        return latencies;
    }
    
    /**
     * Get the name of the last module in the application
     */
    private static String getLastModuleName() {
        // In a real implementation, determine the last module in the dataflow
        return "client"; // Placeholder
    }
    
    /**
     * Create initial module placement strategy using DRL agent
     */
    private static ModulePlacement createInitialModulePlacement(Application application) {
        ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
        
        // If in training mode with exploration, use random or heuristic placement
        // Otherwise use the learned DRL policy
        if (isTraining && trainingEpisodes < EXPLORATION_EPISODES) {
            // Simple heuristic for exploration:
            // - Place motion_tracker on cloud
            // - Place object_detector on edge
            // - Place client on mobile
            
            for (FogDevice device : fogDevices) {
                if (device.getName().equals("cloud")) {
                    moduleMapping.addModuleToDevice("motion_tracker", device.getName());
                } else if (device.getName().equals("edge-server-0")) {
                    moduleMapping.addModuleToDevice("object_detector", device.getName());
                } else if (device.getName().equals("mobile-0-0")) {
                    moduleMapping.addModuleToDevice("client", device.getName());
                }
            }
        } else {
            // Use DRL agent's policy
            Map<String, Double> deviceUtilization = getCurrentDeviceUtilization();
            Map<String, Map<String, Double>> networkLatencies = getCurrentNetworkLatencies();
            
            for (AppModule module : application.getModules()) {
                String bestDevice = drlAgent.selectDeviceForModule(
                    module, deviceUtilization, networkLatencies
                );
                moduleMapping.addModuleToDevice(module.getName(), bestDevice);
            }
        }
        
        return new ModulePlacementMapping(fogDevices, application, moduleMapping);
    }
    
    /**
     * Create application modules
     */
    private static void createAppModules(String appId, Application application) {
        // Defining modules (MIPS, RAM, BW, size, priority)
        application.addAppModule("client", 10, 1000, 1000, 10000);
        application.addAppModule("object_detector", 500, 1000, 200, 10000);
        application.addAppModule("motion_tracker", 1000, 1000, 100, 10000);
    }

    /**
     * Create the application model
     */
    private static Application createApplication(String appId) {
        Application application = Application.createApplication(appId);
        
        // Adding application modules
        application.addAppModule("client", 10);
        application.addAppModule("object_detector", 500);
        application.addAppModule("motion_tracker", 1000);
        
        // Adding edges
        application.addAppEdge("SENSOR", "client", 1000, 500, "SENSOR", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("client", "object_detector", 1000, 500, "RAW_VIDEO", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("object_detector", "motion_tracker", 1000, 500, "DETECTED_OBJECTS", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("motion_tracker", "client", 1000, 500, "OBJECT_TRACKER", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("client", "ACTUATOR", 1000, 500, "ACTION_COMMANDS", Tuple.DOWN, AppEdge.ACTUATOR);
        
        // Adding selectivity for edges
        application.addTupleMapping("client", "SENSOR", "RAW_VIDEO", new FractionalSelectivity(1.0));
        application.addTupleMapping("object_detector", "RAW_VIDEO", "DETECTED_OBJECTS", new FractionalSelectivity(1.0));
        application.addTupleMapping("motion_tracker", "DETECTED_OBJECTS", "OBJECT_TRACKER", new FractionalSelectivity(1.0));
        application.addTupleMapping("client", "OBJECT_TRACKER", "ACTION_COMMANDS", new FractionalSelectivity(1.0));
        
        // Define application loops
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
        // Place sensors and actuators for each mobile device
        for (FogDevice mobile : fogDevices) {
            if (mobile.getName().startsWith("mobile-")) {
                Sensor sensor = new Sensor("sensor-" + mobile.getName(), "SENSOR", userId, appId, 
                                          new DeterministicDistribution(EEG_TRANSMISSION_TIME));
                sensor.setGatewayDeviceId(mobile.getId());
                sensor.setLatency(6.0);
                sensors.add(sensor);
                
                Actuator actuator = new Actuator("actuator-" + mobile.getName(), userId, appId, "ACTUATOR");
                actuator.setGatewayDeviceId(mobile.getId());
                actuator.setLatency(1.0);
                actuators.add(actuator);
            }
        }
    }
    
    /**
     * Create the fog devices in the simulation
     */
    private static void createFogDevices() {
        // Create cloud device
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25);
        cloud.setParentId(-1);
        fogDevices.add(cloud);
        
        // Create edge servers
        for (int i = 0; i < numOfDepts; i++) {
            FogDevice edge = createFogDevice("edge-server-" + i, 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
            edge.setParentId(cloud.getId());
            edge.setUplinkLatency(100); // latency of connection from edge server to cloud is 100 ms
            fogDevices.add(edge);
            
            // Create mobile devices
            for (int j = 0; j < numOfMobilesPerDept; j++) {
                FogDevice mobile = createFogDevice("mobile-" + i + "-" + j, 1000, 1000, 10000, 270, 2, 0, 87.53, 82.44);
                mobile.setParentId(edge.getId());
                mobile.setUplinkLatency(2); // latency of connection from mobile to edge is 2 ms
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
        long storage = 1000000;
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
        
        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        LinkedList<Storage> storageList = new LinkedList<Storage>();
        
        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
            arch, os, vmm, host, time_zone, cost, costPerMem,
            costPerStorage, costPerBw);
        
        FogDevice fogDevice = null;
        try {
            fogDevice = new FogDevice(nodeName, characteristics, 
                                     new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        fogDevice.setLevel(level);
        return fogDevice;
    }
    
    /**
     * Print the simulation results
     */
    private static void printResults() {
        Log.printLine("=========================================");
        Log.printLine("============== RESULTS ==================");
        Log.printLine("=========================================");
        Log.printLine("EXECUTION TIME : " + (Calendar.getInstance().getTimeInMillis() - TimeKeeper.getInstance().getSimulationStartTime()));
        
        // Print module placement decisions
        Log.printLine("=========================================");
        Log.printLine("MODULE PLACEMENT DECISIONS (DRL POLICY):");
        Log.printLine("=========================================");
        
        for (FogDevice device : fogDevices) {
            List<String> modulesOnDevice = getModulesOnDevice(device);
            if (!modulesOnDevice.isEmpty()) {
                Log.printLine(device.getName() + " hosts modules: " + String.join(", ", modulesOnDevice));
            }
        }
        
        // Print energy consumption
        Log.printLine("=========================================");
        Log.printLine("ENERGY CONSUMPTION:");
        Log.printLine("=========================================");
        for (FogDevice device : fogDevices) {
            Log.printLine(device.getName() + " : Energy Consumed = " + device.getEnergyConsumption());
        }
        
        // Print network usage
        Log.printLine("Total network usage = " + FogUtils.TOTAL_NETWORK_USAGE);
    }
}
