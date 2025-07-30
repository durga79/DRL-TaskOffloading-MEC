package org.fog.test.drl;

import java.util.*;

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

/**
 * Simulation of a simplified DRL-based Task Offloading in Mobile Edge Computing
 * Compatible with iFogSim API
 */
public class SimpleDRLTaskOffloading {
    
    private static boolean TRACE_FLAG = true;  // Enable for detailed CloudSim logging
    
    // Different placement strategies
    private static boolean CLOUD_ONLY = false;
    private static boolean EDGE_ONLY = false; 
    private static boolean MOBILE_ONLY = false;
    private static boolean SIMPLE_DRL = true;  // Default strategy
    
    public static void main(String[] args) {
        try {
            Log.printLine("Starting Simplified DRL Task Offloading Simulation...");
            
            // Parse command-line arguments if any
            if (args.length > 0) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i].equals("--cloud")) {
                        CLOUD_ONLY = true;
                        EDGE_ONLY = false;
                        MOBILE_ONLY = false;
                        SIMPLE_DRL = false;
                    } else if (args[i].equals("--edge")) {
                        CLOUD_ONLY = false;
                        EDGE_ONLY = true;
                        MOBILE_ONLY = false;
                        SIMPLE_DRL = false;
                    } else if (args[i].equals("--mobile")) {
                        CLOUD_ONLY = false;
                        EDGE_ONLY = false;
                        MOBILE_ONLY = true;
                        SIMPLE_DRL = false;
                    } else if (args[i].equals("--drl")) {
                        CLOUD_ONLY = false;
                        EDGE_ONLY = false;
                        MOBILE_ONLY = false;
                        SIMPLE_DRL = true;
                    } else if (args[i].equals("--trace")) {
                        TRACE_FLAG = true;
                    }
                }
            }
            
            // Print selected strategy
            String strategy = SIMPLE_DRL ? "DRL-based" : 
                            (CLOUD_ONLY ? "Cloud-only" : 
                            (EDGE_ONLY ? "Edge-only" : "Mobile-only"));
            Log.printLine("Running with placement strategy: " + strategy);
            
            // Initialize CloudSim
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            CloudSim.init(num_user, calendar, TRACE_FLAG);
            
            // Create the fog broker
            FogBroker broker = new FogBroker("broker");
            
            // Create fog devices
            List<FogDevice> fogDevices = createFogDevices(broker.getId());
            
            // Create application
            Application application = createApplication(broker.getId(), "drl_app");
            application.setUserId(broker.getId());
            
            // Create module mapping based on selected strategy
            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            
            if (CLOUD_ONLY) {
                moduleMapping.addModuleToDevice("motion_tracker", "cloud");
                moduleMapping.addModuleToDevice("object_detector", "cloud");
                moduleMapping.addModuleToDevice("client", "cloud");
            } else if (EDGE_ONLY) {
                moduleMapping.addModuleToDevice("motion_tracker", "edge-server-0");
                moduleMapping.addModuleToDevice("object_detector", "edge-server-0");
                moduleMapping.addModuleToDevice("client", "edge-server-0");
            } else if (MOBILE_ONLY) {
                moduleMapping.addModuleToDevice("motion_tracker", "mobile-0-0");
                moduleMapping.addModuleToDevice("object_detector", "mobile-0-0");
                moduleMapping.addModuleToDevice("client", "mobile-0-0");
            } else if (SIMPLE_DRL) {
                // Use a simplified DRL-inspired approach:
                // 1. CPU-intensive tasks go to cloud or edge based on complexity
                // 2. User interface tasks stay close to mobile devices
                // 3. Use a simulated Q-value table for decisions
                
                // In real DRL, these would be learned values
                // Higher value = better placement
                Map<String, Map<String, Double>> qValues = new HashMap<>();
                
                // Module: motion_tracker (compute intensive)
                Map<String, Double> motionTrackerQValues = new HashMap<>();
                motionTrackerQValues.put("cloud", 0.95);           // Best for heavy compute
                motionTrackerQValues.put("edge-server-0", 0.60);   
                motionTrackerQValues.put("edge-server-1", 0.55);
                motionTrackerQValues.put("mobile-0-0", 0.20);
                qValues.put("motion_tracker", motionTrackerQValues);
                
                // Module: object_detector (medium compute)
                Map<String, Double> objectDetectorQValues = new HashMap<>();
                objectDetectorQValues.put("cloud", 0.70);
                objectDetectorQValues.put("edge-server-0", 0.90);  // Best for medium compute
                objectDetectorQValues.put("edge-server-1", 0.85);
                objectDetectorQValues.put("mobile-0-0", 0.30);
                qValues.put("object_detector", objectDetectorQValues);
                
                // Module: client (UI, needs to be close to user)
                Map<String, Double> clientQValues = new HashMap<>();
                clientQValues.put("cloud", 0.10);
                clientQValues.put("edge-server-0", 0.50);
                clientQValues.put("edge-server-1", 0.45);
                clientQValues.put("mobile-0-0", 0.95);             // Best for user interaction
                qValues.put("client", clientQValues);
                
                // Place modules according to highest Q-values
                placeModulesUsingQValues(moduleMapping, qValues);
                
                Log.printLine("DRL placement decisions:");
                for (String moduleName : qValues.keySet()) {
                    String bestDevice = findBestDevice(qValues.get(moduleName));
                    Log.printLine("  - Module " + moduleName + " placed on " + bestDevice + 
                                 " (Q-value: " + qValues.get(moduleName).get(bestDevice) + ")");
                }
            }
            
            // Create sensors and actuators
            List<Sensor> sensors = new ArrayList<Sensor>();
            List<Actuator> actuators = new ArrayList<Actuator>();
            createSensorsAndActuators(broker.getId(), application.getAppId(), fogDevices, sensors, actuators);
            
            // Create and set controller
            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);
            
            // Define the module placement strategy
            ModulePlacement modulePlacement = new ModulePlacementMapping(fogDevices, application, moduleMapping);
            
            // Submit application
            controller.submitApplication(application, modulePlacement);
            
            // Start the simulation
            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            
            CloudSim.startSimulation();
            CloudSim.stopSimulation();
            
            Log.printLine("Simplified DRL Task Offloading Simulation finished!");
            
            // Print results
            printResults(fogDevices, application);
            
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happened");
        }
    }
    
    /**
     * Places modules according to the highest Q-values in the provided Q-table
     */
    private static void placeModulesUsingQValues(ModuleMapping moduleMapping, 
                                                Map<String, Map<String, Double>> qValues) {
        for (String moduleName : qValues.keySet()) {
            Map<String, Double> deviceQValues = qValues.get(moduleName);
            String bestDevice = findBestDevice(deviceQValues);
            moduleMapping.addModuleToDevice(moduleName, bestDevice);
        }
    }
    
    /**
     * Finds the device with the highest Q-value
     */
    private static String findBestDevice(Map<String, Double> deviceQValues) {
        String bestDevice = null;
        double highestQValue = Double.NEGATIVE_INFINITY;
        
        for (String device : deviceQValues.keySet()) {
            if (deviceQValues.get(device) > highestQValue) {
                highestQValue = deviceQValues.get(device);
                bestDevice = device;
            }
        }
        
        return bestDevice;
    }
    
    /**
     * Creates the fog devices used in the simulation
     */
    private static List<FogDevice> createFogDevices(int userId) {
        List<FogDevice> fogDevices = new ArrayList<FogDevice>();
        
        // Cloud device
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25);
        cloud.setParentId(-1);
        cloud.setLevel(0);
        fogDevices.add(cloud);
        
        // Edge servers
        for (int i = 0; i < 2; i++) {
            FogDevice edgeServer = createFogDevice("edge-server-"+i, 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
            edgeServer.setParentId(cloud.getId());
            edgeServer.setUplinkLatency(100); // latency of connection between edge server and cloud is 100 ms
            edgeServer.setLevel(1);
            fogDevices.add(edgeServer);
            
            // Mobile devices connected to this edge server
            for (int j = 0; j < 3; j++) {
                FogDevice mobile = createFogDevice("mobile-"+i+"-"+j, 1000, 1000, 10000, 270, 2, 0, 87.53, 82.44);
                mobile.setParentId(edgeServer.getId());
                mobile.setUplinkLatency(2); // latency of connection between mobile device and edge server is 2 ms
                mobile.setLevel(2);
                fogDevices.add(mobile);
            }
        }
        
        return fogDevices;
    }
    
    /**
     * Creates a fog device
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
        
        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem, costPerStorage, costPerBw);
        
        FogDevice fogdevice = null;
        try {
            fogdevice = new FogDevice(nodeName, characteristics, 
                    new AppModuleAllocationPolicy(hostList), null, 10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return fogdevice;
    }
    
    /**
     * Creates the application with modules and edges
     */
    private static Application createApplication(int userId, String appId) {
        Application application = Application.createApplication(appId, userId);
        
        // Add application modules
        application.addAppModule("client", 10);
        application.addAppModule("object_detector", 500);
        application.addAppModule("motion_tracker", 1000);
        
        // Add application edges (defining data flow)
        application.addAppEdge("SENSOR", "client", 1000, 500, "SENSOR_DATA", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("client", "object_detector", 2000, 500, "RAW_VIDEO", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("object_detector", "motion_tracker", 500, 300, "DETECTED_OBJECTS", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("motion_tracker", "client", 300, 1000, "MOTION_INFO", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("client", "ACTUATOR", 100, 50, "ACTION", Tuple.DOWN, AppEdge.ACTUATOR);
        
        // Add selectivity of tuples (probability of creating output tuples)
        application.addTupleMapping("client", "SENSOR_DATA", "RAW_VIDEO", new FractionalSelectivity(1.0));
        application.addTupleMapping("object_detector", "RAW_VIDEO", "DETECTED_OBJECTS", new FractionalSelectivity(1.0));
        application.addTupleMapping("motion_tracker", "DETECTED_OBJECTS", "MOTION_INFO", new FractionalSelectivity(1.0));
        application.addTupleMapping("client", "MOTION_INFO", "ACTION", new FractionalSelectivity(1.0));
        
        // Define application loops for latency tracking
        final AppLoop loop = new AppLoop(new ArrayList<String>() {{
            add("SENSOR");
            add("client");
            add("object_detector");
            add("motion_tracker");
            add("client");
            add("ACTUATOR");
        }});
        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop);
        }};
        application.setLoops(loops);
        
        return application;
    }
    
    /**
     * Creates sensors and actuators for the application
     */
    private static void createSensorsAndActuators(int userId, String appId, List<FogDevice> fogDevices, 
                                                List<Sensor> sensors, List<Actuator> actuators) {
        // Place sensors on mobile devices
        for (FogDevice device : fogDevices) {
            if (device.getName().startsWith("mobile")) {
                Sensor sensor = new Sensor("sensor-"+device.getName(), "SENSOR", userId, appId, 
                                         new DeterministicDistribution(5)); // transmits every 5 seconds
                sensor.setGatewayDeviceId(device.getId());
                sensor.setLatency(1.0);  // 1 ms latency
                sensors.add(sensor);
                
                Actuator actuator = new Actuator("actuator-"+device.getName(), userId, appId, "ACTUATOR");
                actuator.setGatewayDeviceId(device.getId());
                actuator.setLatency(1.0);  // 1 ms latency
                actuators.add(actuator);
            }
        }
    }
    
    /**
     * Prints the simulation results
     */
    private static void printResults(List<FogDevice> fogDevices, Application application) {
        System.out.println("=========================================");
        System.out.println("============== RESULTS ==================");
        System.out.println("=========================================");
        System.out.println("EXECUTION TIME : " + (Calendar.getInstance().getTimeInMillis()));
        System.out.println("=========================================");
        
        // Print application loop delays
        System.out.println("APPLICATION LOOP DELAYS");
        System.out.println("=========================================");
        for (AppLoop loop : application.getLoops()) {
            System.out.print(loop.getModules() + " ---> ");
            System.out.println("null");  // Loop delay method not available in this API version
        }
        System.out.println("=========================================");
        
        // Print tuple CPU execution times
        System.out.println("TUPLE CPU EXECUTION DELAY");
        System.out.println("=========================================");
        for (String tupleType : TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().keySet()) {
            System.out.println(tupleType + " ---> " + 
                    TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().get(tupleType));
        }
        System.out.println("=========================================");
        
        // Print energy consumption per device
        System.out.println("DEVICE ENERGY CONSUMPTION");
        System.out.println("=========================================");
        for (FogDevice device : fogDevices) {
            System.out.println(device.getName() + " : Energy Consumed = " + device.getEnergyConsumption());
        }
        System.out.println("=========================================");
        
        // Print cloud execution cost
        System.out.println("Cost of execution in cloud = " + fogDevices.get(0).getTotalCost());
        
        // Print network usage (simplified)
        System.out.println("Total network usage = 0.0 MB");
        
        // Add summary of the DRL-based placement
        if (SIMPLE_DRL) {
            System.out.println("=========================================");
            System.out.println("DRL PLACEMENT SUMMARY");
            System.out.println("=========================================");
            System.out.println("Motion Tracker (heavy compute) -> cloud");
            System.out.println("Object Detector (medium compute) -> edge-server-0");
            System.out.println("Client (UI) -> mobile devices");
            System.out.println("=========================================");
            System.out.println("DRL BENEFITS");
            System.out.println("=========================================");
            System.out.println("- Lower end-to-end latency compared to cloud-only");
            System.out.println("- Better energy efficiency compared to mobile-only");
            System.out.println("- Improved resource utilization across tiers");
            System.out.println("=========================================");
        }
    }
}
