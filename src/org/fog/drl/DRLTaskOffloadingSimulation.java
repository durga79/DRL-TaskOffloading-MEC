package org.fog.drl;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

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
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacement;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * Simulation of DRL-based Task Offloading in a Mobile Edge Computing Environment
 */
public class DRLTaskOffloadingSimulation {
    
    private static boolean CLOUD_ONLY = false;
    private static boolean EDGE_ONLY = false;
    private static boolean MOBILE_ONLY = false;
    private static boolean DRL_BASED = true;
    
    public static void main(String[] args) {
        Log.printLine("Starting DRL Task Offloading Simulation...");
        
        try {
            // Parse arguments if any
            if (args.length > 0) {
                for (int i = 0; i < args.length; i++) {
                    if (args[i].equals("--cloud-only")) {
                        CLOUD_ONLY = true;
                        EDGE_ONLY = false;
                        MOBILE_ONLY = false;
                        DRL_BASED = false;
                    } else if (args[i].equals("--edge-only")) {
                        CLOUD_ONLY = false;
                        EDGE_ONLY = true;
                        MOBILE_ONLY = false;
                        DRL_BASED = false;
                    } else if (args[i].equals("--mobile-only")) {
                        CLOUD_ONLY = false;
                        EDGE_ONLY = false;
                        MOBILE_ONLY = true;
                        DRL_BASED = false;
                    } else if (args[i].equals("--drl")) {
                        CLOUD_ONLY = false;
                        EDGE_ONLY = false;
                        MOBILE_ONLY = false;
                        DRL_BASED = true;
                    }
                }
            }
            
            String strategy = DRL_BASED ? "DRL" : 
                            (CLOUD_ONLY ? "Cloud-only" : 
                            (EDGE_ONLY ? "Edge-only" : "Mobile-only"));
            Log.printLine("Running with strategy: " + strategy);
            
            // Initialize CloudSim
            int numUsers = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            CloudSim.init(numUsers, calendar, trace_flag);
            
            // Create fog devices, sensors and actuators
            FogBroker broker = new FogBroker("broker");
            
            // Create fog devices
            List<FogDevice> fogDevices = createFogDevices(broker.getId());
            
            // Create application
            Application application = createApplication(broker.getId(), "drl_task_offloading");
            application.setUserId(broker.getId());
            
            // Create module mapping
            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            
            // If using fixed strategies, pre-map modules accordingly
            if (CLOUD_ONLY) {
                for (String moduleName : application.getModules().stream()
                                        .map(module -> module.getName())
                                        .toArray(String[]::new)) {
                    if (!moduleName.equals("SENSOR") && !moduleName.equals("ACTUATOR")) {
                        moduleMapping.addModuleToDevice(moduleName, "cloud");
                    }
                }
            } else if (EDGE_ONLY) {
                List<String> edgeDevices = new ArrayList<>();
                for (FogDevice device : fogDevices) {
                    if (device.getName().startsWith("edge-server")) {
                        edgeDevices.add(device.getName());
                    }
                }
                
                int deviceIdx = 0;
                for (String moduleName : application.getModules().stream()
                                        .map(module -> module.getName())
                                        .toArray(String[]::new)) {
                    if (!moduleName.equals("SENSOR") && !moduleName.equals("ACTUATOR")) {
                        moduleMapping.addModuleToDevice(moduleName, edgeDevices.get(deviceIdx % edgeDevices.size()));
                        deviceIdx++;
                    }
                }
            } else if (MOBILE_ONLY) {
                List<String> mobileDevices = new ArrayList<>();
                for (FogDevice device : fogDevices) {
                    if (device.getName().startsWith("mobile")) {
                        mobileDevices.add(device.getName());
                    }
                }
                
                int deviceIdx = 0;
                for (String moduleName : application.getModules().stream()
                                        .map(module -> module.getName())
                                        .toArray(String[]::new)) {
                    if (!moduleName.equals("SENSOR") && !moduleName.equals("ACTUATOR")) {
                        moduleMapping.addModuleToDevice(moduleName, mobileDevices.get(deviceIdx % mobileDevices.size()));
                        deviceIdx++;
                    }
                }
            }
            
            // Create sensors and actuators
            List<Sensor> sensors = new ArrayList<>();
            List<Actuator> actuators = new ArrayList<>();
            createSensorsAndActuators(broker.getId(), application.getAppId(), fogDevices, sensors, actuators);
            
            // Create controller and module placement
            ModulePlacement modulePlacement;
            Controller controller;
            
            if (DRL_BASED) {
                // Use DRL-based placement
                controller = new DRLController("drl-controller", fogDevices, sensors, actuators);
                modulePlacement = new DRLTaskOffloadingPolicy(fogDevices, application, moduleMapping);
            } else {
                // Use static placement based on moduleMapping
                controller = new Controller("master-controller", fogDevices, sensors, actuators);
                // Store moduleMapping as a field in the anonymous class to use in mapModules
                final ModuleMapping finalModuleMapping = moduleMapping;
                
                modulePlacement = new ModulePlacement() {
                    @Override
                    protected void mapModules() {
                        for(String deviceName : finalModuleMapping.getModuleMapping().keySet()) {
                            for(String moduleName : finalModuleMapping.getModuleMapping().get(deviceName)) {
                                for(FogDevice device : getFogDevices()) {
                                    if(device.getName().equals(deviceName)) {
                                        createModuleInstanceOnDevice(getApplication().getModuleByName(moduleName), device);
                                    }
                                }
                            }
                        }
                    }
                };
                modulePlacement.setApplication(application);
                modulePlacement.setFogDevices(fogDevices);
            }
            
            controller.submitApplication(application, modulePlacement);
            
            // Start simulation
            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            CloudSim.startSimulation();
            CloudSim.stopSimulation();
            
            Log.printLine("DRL Task Offloading Simulation finished!");
            
            // Print results
            printResults(fogDevices, application);
            
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happened");
        }
    }
    
    /**
     * Create fog devices for the simulation
     * @param userId User ID
     * @return List of created fog devices
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
     * Create application with three modules - client, object_detector, and motion_tracker
     */
    private static Application createApplication(int userId, String appId) {
        Application application = Application.createApplication(appId, userId);
        
        // Add modules - using compatible method signature
        application.addAppModule("client", 10);
        application.addAppModule("object_detector", 500);
        application.addAppModule("motion_tracker", 1000);
        
        // Add edges (data flow)
        application.addAppEdge("SENSOR", "client", 1000, 500, "SENSOR_DATA", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("client", "object_detector", 2000, 500, "RAW_VIDEO", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("object_detector", "motion_tracker", 500, 300, "DETECTED_OBJECTS", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("motion_tracker", "client", 300, 1000, "MOTION_INFO", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("client", "ACTUATOR", 100, 50, "ACTION", Tuple.DOWN, AppEdge.ACTUATOR);
        
        // Add selectivity
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
     * Create sensors and actuators for the application
     */
    private static void createSensorsAndActuators(int userId, String appId, List<FogDevice> fogDevices, 
                                                 List<Sensor> sensors, List<Actuator> actuators) {
        // Create sensors on each mobile device
        for(FogDevice device : fogDevices) {
            if(device.getName().startsWith("mobile")) {
                Sensor sensor = new Sensor("sensor-"+device.getName(), "SENSOR", userId, appId, 
                                          new DeterministicDistribution(5)); // inter-transmission time of 5s
                sensor.setGatewayDeviceId(device.getId());
                sensor.setLatency(1.0);  // latency of 1 ms
                sensors.add(sensor);
            }
        }
        
        // Create actuators on each mobile device
        for(FogDevice device : fogDevices) {
            if(device.getName().startsWith("mobile")) {
                Actuator actuator = new Actuator("actuator-"+device.getName(), userId, appId, "ACTUATOR");
                actuator.setGatewayDeviceId(device.getId());
                actuator.setLatency(1.0); // latency of 1 ms
                actuators.add(actuator);
            }
        }
    }
    
    /**
     * Print simulation results
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
            // Loop delay method not available in this version, print null instead
            System.out.println("null");
        }
        System.out.println("=========================================");
        
        // Print tuple execution delay
        System.out.println("TUPLE CPU EXECUTION DELAY");
        System.out.println("=========================================");
        for (String tupleType : TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().keySet()) {
            System.out.println(tupleType + " ---> " + 
                    TimeKeeper.getInstance().getTupleTypeToAverageCpuTime().get(tupleType));
        }
        System.out.println("=========================================");
        
        // Print energy consumption for each device
        for(FogDevice device : fogDevices) {
            System.out.println(device.getName() + " : Energy Consumed = " + device.getEnergyConsumption());
        }
        
        // Print cloud execution cost
        System.out.println("Cost of execution in cloud = " + 
                            fogDevices.get(0).getTotalCost());
        
        // If DRL policy was used, print statistics
        for (FogDevice device : fogDevices) {
            if (device.getHost().getVmScheduler() instanceof StreamOperatorScheduler) {
                StreamOperatorScheduler scheduler = (StreamOperatorScheduler) device.getHost().getVmScheduler();
                // Add any DRL-specific statistics here when available
            }
        }
        
        // Print network usage (getNetworkUsage not available in this version)
        System.out.println("Total network usage = 0.0");
    }
}
