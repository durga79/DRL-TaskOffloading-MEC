package org.fog.drl;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.fog.entities.*;
import org.fog.application.*;
import org.fog.utils.TimeKeeper;
import org.fog.utils.FogUtils;

import java.util.*;

/**
 * Main simulation class for Deep Reinforcement Learning Task Offloading in Mobile Edge Computing
 * Based on the paper: "Deep Reinforcement Learning for Task Offloading in Mobile Edge Computing" (Wang et al., IEEE INFOCOM 2020)
 */
public class DRLTaskOffloadingSimulation {
    
    private static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
    private static List<Sensor> sensors = new ArrayList<Sensor>();
    private static List<Actuator> actuators = new ArrayList<Actuator>();
    
    // DRL-specific parameters
    private static final int STATE_SIZE = 10;  // Size of the state space
    private static final int ACTION_SIZE = 3;  // Size of the action space (local, edge, cloud)
    private static final double LEARNING_RATE = 0.001;
    private static final double DISCOUNT_FACTOR = 0.95;
    private static final double EXPLORATION_RATE = 0.1;
    
    public static void main(String[] args) {
        try {
            Log.disable();
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            boolean trace_flag = false;
            
            // Initialize CloudSim
            CloudSim.init(num_user, calendar, trace_flag);
            
            // Create broker
            FogBroker broker = new FogBroker("broker");
            
            // Create application
            Application application = createApplication("mobile_app", broker.getId());
            application.setUserId(broker.getId());
            
            // Create fog computing devices (Cloud, Edge servers, Mobile devices)
            createFogDevices(broker.getId());
            
            // Create DRL controller for task offloading
            DRLController controller = new DRLController("controller", fogDevices, sensors, actuators, application);
            controller.submitApplication(application, 0);
            
            // Start simulation
            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            CloudSim.startSimulation();
            
            // Print results
            Log.printLine("Simulation finished!");
            
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happened");
        }
    }
    
    /**
     * Creates the fog devices used in the simulation
     * @param userId User ID
     */
    private static void createFogDevices(int userId) {
        // Create cloud device
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0.01, 16.0, 16.0);
        cloud.setParentId(-1);
        fogDevices.add(cloud);
        
        // Create edge servers
        for (int i = 0; i < 3; i++) {
            FogDevice edgeServer = createFogDevice("edge-" + i, 2800, 4000, 10000, 10000, 0.0, 107.339, 83.4333);
            edgeServer.setParentId(cloud.getId());
            edgeServer.setUplinkLatency(100.0);  // latency of connection between edge server and cloud is 100 ms
            fogDevices.add(edgeServer);
        }
        
        // Create mobile devices
        for (int i = 0; i < 5; i++) {
            FogDevice mobile = createFogDevice("mobile-" + i, 1000, 1000, 1000, 1000, 0.0, 87.53, 82.44);
            mobile.setParentId(fogDevices.get(1 + (i % 3)).getId());  // Connect to an edge server
            mobile.setUplinkLatency(4.0);  // latency of connection between mobile and edge server is 4 ms
            fogDevices.add(mobile);
            
            // Add sensors and actuators to mobile device
            Sensor sensor = new Sensor("sensor-" + i, "SENSOR", userId, "mobile_app", new DeterministicDistribution(5));
            sensor.setGatewayDeviceId(mobile.getId());
            sensor.setLatency(1.0);  // latency of connection between sensor and mobile device is 1 ms
            sensors.add(sensor);
            
            Actuator actuator = new Actuator("actuator-" + i, userId, "mobile_app", "DISPLAY");
            actuator.setGatewayDeviceId(mobile.getId());
            actuator.setLatency(1.0);  // latency of connection between actuator and mobile device is 1 ms
            actuators.add(actuator);
        }
    }
    
    /**
     * Creates a fog device
     * @param name Name of the device
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
     * Creates the application used in the simulation
     * @param appId Application ID
     * @param userId User ID
     * @return Created application
     */
    private static Application createApplication(String appId, int userId) {
        Application application = Application.createApplication(appId, userId);
        
        // Add application modules
        application.addAppModule("client", 10, 50, 50);
        application.addAppModule("object_detector", 250, 1000, 1200);
        application.addAppModule("motion_tracker", 350, 500, 800);
        application.addAppModule("user_interface", 150, 300, 400);
        
        // Add edges
        application.addAppEdge("SENSOR", "client", 1000, 500, "SENSOR_DATA", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("client", "object_detector", 3000, 500, "RAW_VIDEO", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("object_detector", "motion_tracker", 1000, 500, "DETECTED_OBJECTS", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("motion_tracker", "client", 100, 50, "TRACKING_RESULT", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("client", "user_interface", 250, 100, "UI_UPDATE", Tuple.DOWN, AppEdge.MODULE);
        application.addAppEdge("user_interface", "DISPLAY", 100, 50, "VISUALIZATION", Tuple.DOWN, AppEdge.ACTUATOR);
        
        // Add tuple mappings
        application.addTupleMapping("client", "SENSOR_DATA", "RAW_VIDEO", new FractionalSelectivity(0.9));
        application.addTupleMapping("object_detector", "RAW_VIDEO", "DETECTED_OBJECTS", new FractionalSelectivity(1.0));
        application.addTupleMapping("motion_tracker", "DETECTED_OBJECTS", "TRACKING_RESULT", new FractionalSelectivity(1.0));
        application.addTupleMapping("client", "TRACKING_RESULT", "UI_UPDATE", new FractionalSelectivity(1.0));
        application.addTupleMapping("user_interface", "UI_UPDATE", "VISUALIZATION", new FractionalSelectivity(1.0));
        
        // Define application loops to monitor
        final AppLoop loop = new AppLoop(new ArrayList<String>() {{
            add("SENSOR");
            add("client");
            add("object_detector");
            add("motion_tracker");
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
}
