package org.fog.drl;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.*;
import org.fog.placement.*;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

import java.text.DecimalFormat;
import java.util.*;

/**
 * Evaluation class for comparing DRL-based Task Offloading with traditional approaches
 * Based on the paper: "Deep Reinforcement Learning for Task Offloading in Mobile Edge Computing" (Wang et al., IEEE INFOCOM 2020)
 */
public class DRLTaskOffloadingEvaluation {
    
    // Configuration parameters
    private static boolean CLOUD_ONLY = false; // Whether to use cloud-only approach for comparison
    private static boolean EDGE_ONLY = false;  // Whether to use edge-only approach for comparison
    private static boolean MOBILE_ONLY = false; // Whether to use mobile-only approach for comparison
    
    // Network parameters
    private static double CLOUD_LATENCY = 100.0;  // Cloud latency in milliseconds
    private static double EDGE_LATENCY = 10.0;    // Edge latency in milliseconds
    private static double MOBILE_LATENCY = 1.0;   // Mobile device latency in milliseconds
    
    // Simulation parameters
    private static int NUM_EDGE_SERVERS = 5;      // Number of edge servers
    private static int NUM_MOBILE_DEVICES = 10;   // Number of mobile devices
    private static int NUM_APPLICATIONS = 3;      // Number of applications
    private static int NUM_MODULES_PER_APP = 4;   // Number of modules per application
    private static double SIMULATION_TIME = 1000.0; // Simulation time in seconds
    
    // Lists to store fog devices, sensors, actuators, etc.
    private static List<FogDevice> fogDevices = new ArrayList<>();
    private static List<Sensor> sensors = new ArrayList<>();
    private static List<Actuator> actuators = new ArrayList<>();
    private static List<Application> applications = new ArrayList<>();
    
    // Performance tracking
    private static Map<String, Double> latencyResults = new HashMap<>();
    private static Map<String, Double> energyResults = new HashMap<>();
    private static Map<String, Double> executionTimeResults = new HashMap<>();
    
    public static void main(String[] args) {
        try {
            Log.disable();
            int num_user = 1; // Number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // Mean trace events
            
            // Parse command line arguments
            parseCommandLineArguments(args);
            
            // Run simulations with different approaches
            runDRLSimulation(num_user, calendar, trace_flag);
            
            if (CLOUD_ONLY) {
                runCloudOnlySimulation(num_user, calendar, trace_flag);
            }
            
            if (EDGE_ONLY) {
                runEdgeOnlySimulation(num_user, calendar, trace_flag);
            }
            
            if (MOBILE_ONLY) {
                runMobileOnlySimulation(num_user, calendar, trace_flag);
            }
            
            // Compare and print results
            printComparisonResults();
            
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happened");
        }
    }
    
    /**
     * Run simulation using DRL-based task offloading
     */
    private static void runDRLSimulation(int num_user, Calendar calendar, boolean trace_flag) {
        try {
            // Initialize CloudSim
            CloudSim.init(num_user, calendar, trace_flag);
            
            // Create broker
            FogBroker broker = new FogBroker("broker");
            
            // Create fog computing devices (Cloud, Edge servers, Mobile devices)
            createFogDevices(broker.getId());
            
            // Create applications
            for (int i = 0; i < NUM_APPLICATIONS; i++) {
                String appId = "app_" + i;
                Application application = createApplication(appId, broker.getId());
                application.setUserId(broker.getId());
                applications.add(application);
            }
            
            // Create DRL controller for task offloading
            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // This will be dynamically updated by the controller
            
            // Create DRL task offloading policy
            DRLTaskOffloadingPolicy policy = new DRLTaskOffloadingPolicy(fogDevices);
            
            // Create controller with DRL policy
            Controller controller = new Controller("master-controller", fogDevices, sensors, 
                                                 actuators, applications.get(0), moduleMapping);
            
            controller.submitApplication(applications.get(0), 0, policy);
            
            // Start the simulation
            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            
            long startTime = System.currentTimeMillis();
            CloudSim.startSimulation();
            
            // Calculate execution time
            long executionTime = System.currentTimeMillis() - startTime;
            executionTimeResults.put("DRL", (double)executionTime);
            
            // Extract results
            latencyResults.put("DRL", TimeKeeper.getInstance().getAverageLoopDelay());
            energyResults.put("DRL", calculateTotalEnergy());
            
            // Print DRL-specific results
            System.out.println("DRL Approach - Performance Report");
            System.out.println("==================================");
            System.out.println(policy.generatePerformanceReport());
            
            // Reset for next simulation
            fogDevices.clear();
            sensors.clear();
            actuators.clear();
            applications.clear();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Run simulation using cloud-only approach
     */
    private static void runCloudOnlySimulation(int num_user, Calendar calendar, boolean trace_flag) {
        try {
            // Initialize CloudSim
            CloudSim.init(num_user, calendar, trace_flag);
            
            // Create broker
            FogBroker broker = new FogBroker("broker");
            
            // Create fog computing devices (Cloud, Edge servers, Mobile devices)
            createFogDevices(broker.getId());
            
            // Create application
            String appId = "app_cloud_only";
            Application application = createApplication(appId, broker.getId());
            application.setUserId(broker.getId());
            
            // Set all modules to be placed on cloud
            for (String moduleName : application.getModules()) {
                application.setSpecialPlacementInfo(moduleName, "cloud");
            }
            
            applications.add(application);
            
            // Create module mapping
            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            
            // Map all modules to cloud
            for (FogDevice device : fogDevices) {
                if (device.getName().startsWith("cloud")) {
                    for (String moduleName : application.getModules()) {
                        moduleMapping.addModuleToDevice(moduleName, device.getName());
                    }
                }
            }
            
            // Create controller
            Controller controller = new Controller("master-controller", fogDevices, sensors, 
                                                 actuators, application);
            
            controller.submitApplication(application, moduleMapping);
            
            // Start the simulation
            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            
            long startTime = System.currentTimeMillis();
            CloudSim.startSimulation();
            
            // Calculate execution time
            long executionTime = System.currentTimeMillis() - startTime;
            executionTimeResults.put("CloudOnly", (double)executionTime);
            
            // Extract results
            latencyResults.put("CloudOnly", TimeKeeper.getInstance().getAverageLoopDelay());
            energyResults.put("CloudOnly", calculateTotalEnergy());
            
            // Reset for next simulation
            fogDevices.clear();
            sensors.clear();
            actuators.clear();
            applications.clear();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Run simulation using edge-only approach
     */
    private static void runEdgeOnlySimulation(int num_user, Calendar calendar, boolean trace_flag) {
        try {
            // Initialize CloudSim
            CloudSim.init(num_user, calendar, trace_flag);
            
            // Create broker
            FogBroker broker = new FogBroker("broker");
            
            // Create fog computing devices (Cloud, Edge servers, Mobile devices)
            createFogDevices(broker.getId());
            
            // Create application
            String appId = "app_edge_only";
            Application application = createApplication(appId, broker.getId());
            application.setUserId(broker.getId());
            applications.add(application);
            
            // Create module mapping
            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            
            // Map modules to edge devices in a round-robin fashion
            List<FogDevice> edgeDevices = new ArrayList<>();
            for (FogDevice device : fogDevices) {
                if (device.getName().startsWith("edge-")) {
                    edgeDevices.add(device);
                }
            }
            
            int edgeIndex = 0;
            for (String moduleName : application.getModules()) {
                FogDevice targetDevice = edgeDevices.get(edgeIndex % edgeDevices.size());
                moduleMapping.addModuleToDevice(moduleName, targetDevice.getName());
                edgeIndex++;
            }
            
            // Create controller
            Controller controller = new Controller("master-controller", fogDevices, sensors, 
                                                 actuators, application);
            
            controller.submitApplication(application, moduleMapping);
            
            // Start the simulation
            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            
            long startTime = System.currentTimeMillis();
            CloudSim.startSimulation();
            
            // Calculate execution time
            long executionTime = System.currentTimeMillis() - startTime;
            executionTimeResults.put("EdgeOnly", (double)executionTime);
            
            // Extract results
            latencyResults.put("EdgeOnly", TimeKeeper.getInstance().getAverageLoopDelay());
            energyResults.put("EdgeOnly", calculateTotalEnergy());
            
            // Reset for next simulation
            fogDevices.clear();
            sensors.clear();
            actuators.clear();
            applications.clear();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Run simulation using mobile-only approach
     */
    private static void runMobileOnlySimulation(int num_user, Calendar calendar, boolean trace_flag) {
        try {
            // Initialize CloudSim
            CloudSim.init(num_user, calendar, trace_flag);
            
            // Create broker
            FogBroker broker = new FogBroker("broker");
            
            // Create fog computing devices (Cloud, Edge servers, Mobile devices)
            createFogDevices(broker.getId());
            
            // Create application
            String appId = "app_mobile_only";
            Application application = createApplication(appId, broker.getId());
            application.setUserId(broker.getId());
            applications.add(application);
            
            // Create module mapping
            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            
            // Map modules to mobile devices that have their sensors
            Map<Integer, List<String>> deviceToModules = new HashMap<>();
            
            // Map sensors to their gateway devices
            Map<Integer, List<String>> sensorToDevice = new HashMap<>();
            for (Sensor sensor : sensors) {
                int deviceId = sensor.getGatewayDeviceId();
                if (!sensorToDevice.containsKey(deviceId)) {
                    sensorToDevice.put(deviceId, new ArrayList<>());
                }
                sensorToDevice.get(deviceId).add(sensor.getName());
            }
            
            // For each module, find a device that has a related sensor
            for (String moduleName : application.getModules()) {
                // Simplified mapping - place each module on the device of the first sensor
                if (!sensors.isEmpty()) {
                    int deviceId = sensors.get(0).getGatewayDeviceId();
                    
                    // Find the device
                    for (FogDevice device : fogDevices) {
                        if (device.getId() == deviceId) {
                            moduleMapping.addModuleToDevice(moduleName, device.getName());
                            break;
                        }
                    }
                }
            }
            
            // Create controller
            Controller controller = new Controller("master-controller", fogDevices, sensors, 
                                                 actuators, application);
            
            controller.submitApplication(application, moduleMapping);
            
            // Start the simulation
            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            
            long startTime = System.currentTimeMillis();
            CloudSim.startSimulation();
            
            // Calculate execution time
            long executionTime = System.currentTimeMillis() - startTime;
            executionTimeResults.put("MobileOnly", (double)executionTime);
            
            // Extract results
            latencyResults.put("EdgeOnly", TimeKeeper.getInstance().getAverageLoopDelay());
            energyResults.put("EdgeOnly", calculateTotalEnergy());
            
            // Reset for next simulation
            fogDevices.clear();
            sensors.clear();
            actuators.clear();
            applications.clear();
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Print comparison results between different approaches
     */
    private static void printComparisonResults() {
        System.out.println("\nCOMPARISON RESULTS");
        System.out.println("==================\n");
        
        DecimalFormat df = new DecimalFormat("#.##");
        
        System.out.println("Latency Comparison (ms):");
        System.out.println("------------------------");
        for (Map.Entry<String, Double> entry : latencyResults.entrySet()) {
            System.out.println(entry.getKey() + ": " + df.format(entry.getValue()));
        }
        System.out.println();
        
        System.out.println("Energy Consumption Comparison (J):");
        System.out.println("--------------------------------");
        for (Map.Entry<String, Double> entry : energyResults.entrySet()) {
            System.out.println(entry.getKey() + ": " + df.format(entry.getValue()));
        }
        System.out.println();
        
        System.out.println("Execution Time Comparison (ms):");
        System.out.println("-----------------------------");
        for (Map.Entry<String, Double> entry : executionTimeResults.entrySet()) {
            System.out.println(entry.getKey() + ": " + df.format(entry.getValue()));
        }
        System.out.println();
        
        // Calculate improvement percentages if DRL results exist
        if (latencyResults.containsKey("DRL")) {
            double drlLatency = latencyResults.get("DRL");
            
            System.out.println("DRL Improvement Percentages:");
            System.out.println("---------------------------");
            
            if (latencyResults.containsKey("CloudOnly")) {
                double improvement = (latencyResults.get("CloudOnly") - drlLatency) / latencyResults.get("CloudOnly") * 100;
                System.out.println("Latency improvement over Cloud-only: " + df.format(improvement) + "%");
            }
            
            if (latencyResults.containsKey("EdgeOnly")) {
                double improvement = (latencyResults.get("EdgeOnly") - drlLatency) / latencyResults.get("EdgeOnly") * 100;
                System.out.println("Latency improvement over Edge-only: " + df.format(improvement) + "%");
            }
            
            if (latencyResults.containsKey("MobileOnly")) {
                double improvement = (latencyResults.get("MobileOnly") - drlLatency) / latencyResults.get("MobileOnly") * 100;
                System.out.println("Latency improvement over Mobile-only: " + df.format(improvement) + "%");
            }
            System.out.println();
        }
    }
    
    /**
     * Create fog devices including cloud, edge servers, and mobile devices
     * @param userId User ID
     */
    private static void createFogDevices(int userId) {
        // Create cloud device
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0.01, 16.0, 16.0);
        cloud.setParentId(-1);
        fogDevices.add(cloud);
        
        // Create edge servers
        for (int i = 0; i < NUM_EDGE_SERVERS; i++) {
            FogDevice edgeServer = createFogDevice("edge-" + i, 2800, 4000, 10000, 10000, 0.0, 107.339, 83.4333);
            edgeServer.setParentId(cloud.getId());
            edgeServer.setUplinkLatency(CLOUD_LATENCY);  // latency between edge server and cloud
            fogDevices.add(edgeServer);
        }
        
        // Create mobile devices
        for (int i = 0; i < NUM_MOBILE_DEVICES; i++) {
            FogDevice mobile = createFogDevice("mobile-" + i, 1000, 1000, 1000, 1000, 0.0, 87.53, 82.44);
            mobile.setParentId(fogDevices.get(1 + (i % NUM_EDGE_SERVERS)).getId());  // Connect to an edge server
            mobile.setUplinkLatency(EDGE_LATENCY);  // latency between mobile and edge server
            fogDevices.add(mobile);
            
            // Add sensors and actuators to mobile device
            Sensor sensor = new Sensor("sensor-" + i, "SENSOR", userId, "app_0", new DeterministicDistribution(5));
            sensor.setGatewayDeviceId(mobile.getId());
            sensor.setLatency(MOBILE_LATENCY);  // latency of connection between sensor and mobile device
            sensors.add(sensor);
            
            Actuator actuator = new Actuator("actuator-" + i, userId, "app_0", "DISPLAY");
            actuator.setGatewayDeviceId(mobile.getId());
            actuator.setLatency(MOBILE_LATENCY);  // latency of connection between actuator and mobile device
            actuators.add(actuator);
        }
    }
    
    /**
     * Create a fog device
     * @param name Device name
     * @param mips MIPS
     * @param ram RAM
     * @param upBw Uplink bandwidth
     * @param downBw Downlink bandwidth
     * @param ratePerMips Cost rate per MIPS
     * @param busyPower Power consumption when CPU is busy
     * @param idlePower Power consumption when CPU is idle
     * @return Created fog device
     */
    private static FogDevice createFogDevice(String name, long mips, int ram, long upBw, long downBw, 
                                            double ratePerMips, double busyPower, double idlePower) {
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
        
        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem, costPerStorage, costPerBw
        );
        
        FogDevice fogdevice = new FogDevice(name, characteristics, 
                new AppModuleAllocationPolicy(hostList), new LinkedList<Sensor>(), 
                new LinkedList<Actuator>(), FogUtils.generateEntityId(), ratePerMips);
        
        fogdevice.setLevel(calculateLevel(name));
        return fogdevice;
    }
    
    /**
     * Calculate device level in the hierarchy based on its name
     * @param name Device name
     * @return Level
     */
    private static int calculateLevel(String name) {
        if(name.startsWith("cloud"))
            return 0;
        else if(name.startsWith("edge"))
            return 1;
        else if(name.startsWith("mobile"))
            return 2;
        else
            return -1;
    }
    
    /**
     * Create an application with multiple modules
     * @param appId Application ID
     * @param userId User ID
     * @return Created application
     */
    private static Application createApplication(String appId, int userId) {
        Application application = Application.createApplication(appId, userId);
        
        // Add application modules with varying resource requirements
        application.addAppModule("client", 10, 50, 50);
        application.addAppModule("feature_extractor", 250, 1000, 1200);
        application.addAppModule("analyzer", 350, 500, 800);
        application.addAppModule("user_interface", 150, 300, 400);
        
        // Add edges connecting modules
        application.addAppEdge("SENSOR", "client", 1000, 500, "SENSOR_DATA", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("client", "feature_extractor", 3000, 500, "RAW_DATA", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("feature_extractor", "analyzer", 1000, 500, "FEATURES", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("analyzer", "client", 100, 50, "ANALYSIS_RESULT", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("client", "user_interface", 250, 100, "UI_UPDATE", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("user_interface", "DISPLAY", 100, 50, "VISUALIZATION", Tuple.DOWN, AppEdge.ACTUATOR);
        
        // Add tuple mappings
        application.addTupleMapping("client", "SENSOR_DATA", "RAW_DATA", new FractionalSelectivity(0.9));
        application.addTupleMapping("feature_extractor", "RAW_DATA", "FEATURES", new FractionalSelectivity(1.0));
        application.addTupleMapping("analyzer", "FEATURES", "ANALYSIS_RESULT", new FractionalSelectivity(1.0));
        application.addTupleMapping("client", "ANALYSIS_RESULT", "UI_UPDATE", new FractionalSelectivity(1.0));
        application.addTupleMapping("user_interface", "UI_UPDATE", "VISUALIZATION", new FractionalSelectivity(1.0));
        
        // Define application loops to monitor
        final AppLoop loop = new AppLoop(new ArrayList<String>() {{
            add("SENSOR");
            add("client");
            add("feature_extractor");
            add("analyzer");
            add("client");
            add("user_interface");
            add("DISPLAY");
        }});
        
        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop);
        }};
        
        application.setLoops(loops);
        
        return application;
    }
    
    /**
     * Calculate total energy consumption from all devices
     * @return Total energy
     */
    private static double calculateTotalEnergy() {
        double totalEnergy = 0.0;
        
        for (FogDevice device : fogDevices) {
            double deviceEnergy = device.getEnergyConsumption();
            totalEnergy += deviceEnergy;
        }
        
        return totalEnergy;
    }
    
    /**
     * Parse command line arguments
     * @param args Command line arguments
     */
    private static void parseCommandLineArguments(String[] args) {
        if (args.length > 0) {
            for (int i = 0; i < args.length; i++) {
                if (args[i].equals("--cloud-only")) {
                    CLOUD_ONLY = true;
                } else if (args[i].equals("--edge-only")) {
                    EDGE_ONLY = true;
                } else if (args[i].equals("--mobile-only")) {
                    MOBILE_ONLY = true;
                } else if (args[i].equals("--all")) {
                    CLOUD_ONLY = true;
                    EDGE_ONLY = true;
                    MOBILE_ONLY = true;
                } else if (args[i].equals("--edge-servers") && i + 1 < args.length) {
                    NUM_EDGE_SERVERS = Integer.parseInt(args[++i]);
                } else if (args[i].equals("--mobile-devices") && i + 1 < args.length) {
                    NUM_MOBILE_DEVICES = Integer.parseInt(args[++i]);
                }
            }
        } else {
            // Default is to run all simulations
            CLOUD_ONLY = true;
            EDGE_ONLY = true;
            MOBILE_ONLY = true;
        }
    }
}
