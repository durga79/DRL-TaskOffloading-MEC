package org.fog.test.drl;

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
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * DRL Task Offloading Simulation - Compatible with iFogSim API
 */
public class DRLTaskOffloadingTest {
    
    static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    static List<Sensor> sensors = new ArrayList<Sensor>();
    static List<Actuator> actuators = new ArrayList<Actuator>();
    
    // Configuration parameters
    static int numOfEdgeServers = 2;
    static int numOfMobilesPerEdge = 3;
    static double SENSOR_TRANSMISSION_TIME = 5;
    
    public static void main(String[] args) {
        try {
            Log.disable(); // Disable CloudSim logging
            
            Log.printLine("Starting DRL Task Offloading Simulation...");
            
            int num_user = 1; // Number of cloud users
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false; // Mean trace events
            
            // Initialize CloudSim
            CloudSim.init(num_user, calendar, trace_flag);
            
            // Create broker
            FogBroker broker = new FogBroker("broker");
            
            String appId = "drl_task_offloading";
            
            // Create application
            Application application = createApplication(appId, broker.getId());
            application.setUserId(broker.getId());
            
            // Create devices (cloud, edge servers, mobile devices)
            createFogDevices(broker.getId(), appId);
            
            // Create module mapping
            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            
            // Map modules to devices
            moduleMapping.addModuleToDevice("motion_tracker", "cloud"); // Heavy computation on cloud
            
            for(int i=0; i<numOfEdgeServers; i++) {
                moduleMapping.addModuleToDevice("object_detector", "edge-server-"+i); // Medium computation on edge
            }
            
            // Place client module on all mobile devices
            for(FogDevice device : fogDevices) {
                if(device.getName().startsWith("mobile-")) {
                    moduleMapping.addModuleToDevice("client", device.getName());
                }
            }
            
            // Create controller
            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);
            
            // Submit application
            controller.submitApplication(application, 
                    new ModulePlacementMapping(fogDevices, application, moduleMapping));
            
            // Start simulation
            CloudSim.startSimulation();
            
            // Stop simulation
            CloudSim.stopSimulation();
            
            // Print results
            Log.printLine("=========================================");
            Log.printLine("============== RESULTS ==================");
            Log.printLine("=========================================");
            Log.printLine("EXECUTION TIME : " + (Calendar.getInstance().getTimeInMillis() - calendar.getTimeInMillis()));
            Log.printLine("=========================================");
            
            // Print application loop delays
            Log.printLine("APPLICATION LOOP DELAYS");
            Log.printLine("=========================================");
            for(AppLoop loop : application.getLoops()){
                Log.printLine(loop.getLoopId() + " : " + loop.getModules());
            }
            
            // Print energy consumption for each device
            Log.printLine("ENERGY CONSUMPTION");
            Log.printLine("=========================================");
            double totalEnergy = 0.0;
            for(FogDevice device : fogDevices) {
                double deviceEnergy = device.getEnergyConsumption();
                Log.printLine(device.getName() + " : Energy Consumed = " + deviceEnergy);
                totalEnergy += deviceEnergy;
            }
            Log.printLine("Total Energy Consumption = " + totalEnergy);
            Log.printLine("=========================================");
            
            // Network usage metrics would go here
            Log.printLine("==========================================");
            
            Log.printLine("DRL Task Offloading Simulation completed.");
            
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happened");
        }
    }
    
    /**
     * Creates fog devices (cloud, edge servers, mobile devices)
     */
    private static void createFogDevices(int userId, String appId) {
        // Create cloud device
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16*103, 16*83.25);
        cloud.setParentId(-1);
        fogDevices.add(cloud);
        
        // Create edge servers
        for(int i=0; i<numOfEdgeServers; i++) {
            FogDevice edgeServer = createFogDevice("edge-server-"+i, 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
            edgeServer.setParentId(cloud.getId());
            edgeServer.setUplinkLatency(100); // latency of connection to cloud in ms
            fogDevices.add(edgeServer);
            
            // Create mobile devices connected to this edge server
            for(int j=0; j<numOfMobilesPerEdge; j++) {
                String mobileDeviceName = "mobile-" + i + "-" + j;
                FogDevice mobile = createFogDevice(mobileDeviceName, 1000, 1000, 10000, 270, 2, 0, 87.53, 82.44);
                mobile.setParentId(edgeServer.getId());
                mobile.setUplinkLatency(2); // latency of connection to edge server in ms
                fogDevices.add(mobile);
                
                // Create a sensor attached to the mobile device
                Sensor sensor = new Sensor("sensor-"+i+"-"+j, "SENSOR", userId, appId, new DeterministicDistribution(SENSOR_TRANSMISSION_TIME));
                sensor.setGatewayDeviceId(mobile.getId());
                sensor.setLatency(2.0); // latency of connection to mobile device
                sensors.add(sensor);
                
                // Create an actuator attached to the mobile device
                Actuator actuator = new Actuator("actuator-"+i+"-"+j, userId, appId, "ACTUATOR");
                actuator.setGatewayDeviceId(mobile.getId());
                actuator.setLatency(1.0); // latency of connection to mobile device
                actuators.add(actuator);
            }
        }
    }
    
    /**
     * Creates a fog device
     */
    private static FogDevice createFogDevice(String nodeName, long mips,
            int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
        
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
        LinkedList<Storage> storageList = new LinkedList<Storage>(); // We are not adding SAN devices by now
        
        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);
        
        FogDevice fogdevice = null;
        try {
            fogdevice = new FogDevice(nodeName, characteristics, 
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
            
            fogdevice.setLevel(level);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return fogdevice;
    }
    
    /**
     * Creates the mobile edge computing application
     */
    @SuppressWarnings({"serial"})
    private static Application createApplication(String appId, int userId) {
        
        Application application = Application.createApplication(appId, userId);
        
        // Add application modules with resource requirements (MIPS, RAM, Size)
        application.addAppModule("client", 10); // lightweight client
        application.addAppModule("object_detector", 250); // medium computation
        application.addAppModule("motion_tracker", 350); // heavy computation
        
        // Add edges connecting modules
        application.addAppEdge("SENSOR", "client", 3000, 500, "SENSOR_DATA", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("client", "object_detector", 4000, 500, "RAW_DATA", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("object_detector", "motion_tracker", 1000, 500, "DETECTED_OBJECTS", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("motion_tracker", "client", 100, 500, "PROCESSED_DATA", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("client", "ACTUATOR", 100, 50, "ACTION_COMMAND", Tuple.DOWN, AppEdge.ACTUATOR);
        
        // Add tuple mappings
        application.addTupleMapping("client", "SENSOR_DATA", "RAW_DATA", new FractionalSelectivity(0.9));
        application.addTupleMapping("object_detector", "RAW_DATA", "DETECTED_OBJECTS", new FractionalSelectivity(1.0));
        application.addTupleMapping("motion_tracker", "DETECTED_OBJECTS", "PROCESSED_DATA", new FractionalSelectivity(1.0));
        application.addTupleMapping("client", "PROCESSED_DATA", "ACTION_COMMAND", new FractionalSelectivity(1.0));
        
        // Define application loops to monitor
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
}
