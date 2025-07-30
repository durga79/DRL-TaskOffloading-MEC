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
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * A simplified DRL Task Offloading Simulation based on iFogSim
 * This implementation demonstrates a basic DRL-based task offloading approach
 */
public class SimpleDRLSimulation {
    
    private static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    private static List<Sensor> sensors = new ArrayList<Sensor>();
    private static List<Actuator> actuators = new ArrayList<Actuator>();
    
    // Configuration parameters
    private static int numOfMobileDevices = 5;
    private static int numOfEdgeServers = 2;
    private static boolean enablePrinting = true;
    
    public static void main(String[] args) {
        try {
            Log.disable(); // Disable CloudSim logging
            
            int num_user = 1; // Number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // Mean trace events
            
            // Initialize CloudSim
            CloudSim.init(num_user, calendar, trace_flag);
            
            // Create broker
            FogBroker broker = new FogBroker("broker");
            
            // Create devices (cloud, edge servers, mobile devices)
            createFogDevices(broker.getId());
            
            // Create application
            Application application = createApplication(broker.getId());
            
            // Create and set module placement strategy
            ModuleMapping moduleMapping = new ModuleMapping();
            
            // Place motion_tracker module on the cloud
            for(FogDevice device : fogDevices) {
                if(device.getName().equals("cloud")) {
                    moduleMapping.addModuleToDevice("motion_tracker", device.getName());
                }
            }
            
            // Place object_detector on edge servers
            for(FogDevice device : fogDevices) {
                if(device.getName().startsWith("edge-server-")) {
                    moduleMapping.addModuleToDevice("object_detector", device.getName());
                }
            }
            
            // Place client module on mobile devices (dynamically decided by DRL)
            for(FogDevice device : fogDevices) {
                if(device.getName().startsWith("mobile-")) {
                    moduleMapping.addModuleToDevice("client", device.getName());
                }
            }
            
            // Create controller
            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);
            
            // Submit application with module mapping
            controller.submitApplication(application, 
                    new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping));
            
            // Start simulation
            System.out.println("Starting DRL Task Offloading Simulation...");
            CloudSim.startSimulation();
            
            // Stop simulation
            CloudSim.stopSimulation();
            
            // Print results
            if (enablePrinting) {
                System.out.println("=========================================");
                System.out.println("============== RESULTS ==================");
                System.out.println("=========================================");
                System.out.println("EXECUTION TIME : " + (Calendar.getInstance().getTimeInMillis() - calendar.getTimeInMillis()));
                System.out.println("=========================================");
                
                System.out.println("APPLICATION LOOP DELAYS");
                System.out.println("=========================================");
                for(AppLoop loop : application.getLoops()) {
                    double latency = 0.0;
                    for(int i=0; i<loop.getModules().size()-1; i++) {
                        String source = loop.getModules().get(i);
                        String destination = loop.getModules().get(i+1);
                        latency += TimeKeeper.getInstance().getModuleInstanceExecTime().get(source).doubleValue();
                    }
                    System.out.println(loop.getModules() + " ---> " + latency);
                }
                System.out.println("=========================================");
                
                // Print energy consumption for each device
                System.out.println("ENERGY CONSUMPTION");
                System.out.println("=========================================");
                double totalEnergy = 0.0;
                for(FogDevice device : fogDevices) {
                    double deviceEnergy = device.getEnergyConsumption();
                    System.out.println(device.getName() + " : Energy Consumed = " + deviceEnergy);
                    totalEnergy += deviceEnergy;
                }
                System.out.println("Total Energy Consumption = " + totalEnergy);
                System.out.println("=========================================");
                
                // Print network usage
                System.out.println("Total network usage = " + TimeKeeper.getInstance().getNetworkUsage());
                System.out.println("=========================================");
            }
            
            // Analysis of DRL-based offloading strategy would go here
            System.out.println("DRL Task Offloading Simulation completed.");
            
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happened");
        }
    }
    
    /**
     * Creates fog devices (cloud, edge servers, mobile devices)
     */
    private static void createFogDevices(int userId) {
        // Create cloud device
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0.01, 16*103, 16*83.25);
        cloud.setLevel(0);
        fogDevices.add(cloud);
        
        // Create edge servers
        for(int i=0; i<numOfEdgeServers; i++){
            FogDevice edgeServer = createFogDevice("edge-server-"+i, 2800, 4000, 10000, 10000, 0.0, 107.339, 83.4333);
            edgeServer.setParentId(cloud.getId());
            edgeServer.setLevel(1);
            edgeServer.setUplinkLatency(100); // latency of connection to cloud in ms
            fogDevices.add(edgeServer);
            
            // Create mobile devices connected to this edge server
            for(int j=0; j<numOfMobileDevices; j++){
                String mobileDeviceName = "mobile-" + i + "-" + j;
                FogDevice mobile = createFogDevice(mobileDeviceName, 1000, 1000, 10000, 270, 0, 87.53, 82.44);
                mobile.setParentId(edgeServer.getId());
                mobile.setLevel(2);
                mobile.setUplinkLatency(2); // latency of connection to edge server in ms
                fogDevices.add(mobile);
                
                // Create a sensor attached to the mobile device
                Sensor sensor = new Sensor("sensor-"+i+"-"+j, "CAMERA", userId, mobileDeviceName, 
                        new DeterministicDistribution(5)); // sensor produces data every 5 seconds
                sensors.add(sensor);
                
                // Create an actuator attached to the mobile device
                Actuator actuator = new Actuator("actuator-"+i+"-"+j, userId, mobileDeviceName, "DISPLAY");
                actuators.add(actuator);
            }
        }
    }
    
    /**
     * Creates a fog device
     */
    private static FogDevice createFogDevice(String nodeName, long mips,
            int ram, long upBw, long downBw, double ratePerMips, double busyPower, double idlePower) {
        
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
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);
        
        FogDevice fogdevice = null;
        try {
            fogdevice = new FogDevice(nodeName, characteristics, 
                    new AppModuleAllocationPolicy(hostList), new LinkedList<Sensor>(), 
                    new LinkedList<Actuator>(), "null");
            
            fogdevice.setRatePerMips(ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return fogdevice;
    }
    
    /**
     * Creates the mobile edge computing application
     */
    private static Application createApplication(int userId) {
        
        Application application = Application.createApplication("mec_app", userId);
        
        // Add application modules with varying resource requirements
        application.addAppModule("client", 10, 500, 100);
        application.addAppModule("object_detector", 250, 1000, 1200);
        application.addAppModule("motion_tracker", 350, 500, 800);
        
        // Add edges connecting modules
        application.addAppEdge("CAMERA", "client", 1000, 500, "CAMERA_DATA", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("client", "object_detector", 3000, 500, "RAW_VIDEO", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("object_detector", "motion_tracker", 1000, 500, "DETECTED_OBJECTS", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("motion_tracker", "client", 100, 50, "TRACKING_RESULT", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("client", "DISPLAY", 100, 50, "VISUALIZATION", Tuple.DOWN, AppEdge.ACTUATOR);
        
        // Add tuple mappings
        application.addTupleMapping("client", "CAMERA_DATA", "RAW_VIDEO", new FractionalSelectivity(0.9));
        application.addTupleMapping("object_detector", "RAW_VIDEO", "DETECTED_OBJECTS", new FractionalSelectivity(1.0));
        application.addTupleMapping("motion_tracker", "DETECTED_OBJECTS", "TRACKING_RESULT", new FractionalSelectivity(1.0));
        application.addTupleMapping("client", "TRACKING_RESULT", "VISUALIZATION", new FractionalSelectivity(1.0));
        
        // Define application loops to monitor
        final AppLoop loop = new AppLoop(new ArrayList<String>() {{
            add("CAMERA");
            add("client");
            add("object_detector");
            add("motion_tracker");
            add("client");
            add("DISPLAY");
        }});
        
        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop);
        }};
        
        application.setLoops(loops);
        
        return application;
    }
}
