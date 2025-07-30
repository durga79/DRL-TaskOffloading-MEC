package org.fog.drl;

import org.fog.entities.FogDevice;
import org.fog.entities.MobilityController;
import org.fog.entities.MobilityModel;
import org.fog.entities.Sensor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Utility class for managing Mobile Edge Computing (MEC) environment
 * Handles device mobility patterns, network conditions, and server loads
 */
public class MECEnvironmentUtil {

    // Random number generator for stochastic processes
    private static Random random = new Random();
    
    // Mobility patterns for mobile devices
    private static Map<Integer, MobilityModel> mobilityModels = new HashMap<>();
    
    // Current network conditions (latency, bandwidth)
    private static Map<String, Double> networkLatencies = new HashMap<>();
    private static Map<String, Double> networkBandwidths = new HashMap<>();
    
    // Server load history for prediction
    private static Map<Integer, List<Double>> serverLoadHistory = new HashMap<>();
    
    /**
     * Initialize the MEC environment with device mobility models
     * @param mobileFogDevices List of mobile fog devices
     * @param edgeServers List of edge servers
     */
    public static void initEnvironment(List<FogDevice> mobileFogDevices, List<FogDevice> edgeServers) {
        // Initialize mobility models for each mobile device
        for (FogDevice device : mobileFogDevices) {
            // Random waypoint mobility model
            MobilityModel mobilityModel = new MobilityModel();
            mobilityModels.put(device.getId(), mobilityModel);
        }
        
        // Initialize network conditions
        for (FogDevice mobileDevice : mobileFogDevices) {
            for (FogDevice edgeServer : edgeServers) {
                // Initialize latency between each mobile device and edge server
                String linkKey = mobileDevice.getId() + "-" + edgeServer.getId();
                double baseLatency = 10.0 + random.nextDouble() * 10.0; // Base latency between 10-20ms
                networkLatencies.put(linkKey, baseLatency);
                
                // Initialize bandwidth
                double baseBandwidth = 10.0 + random.nextDouble() * 20.0; // Base bandwidth between 10-30 Mbps
                networkBandwidths.put(linkKey, baseBandwidth);
            }
        }
    }
    
    /**
     * Update device mobility and network conditions
     * @param currentTime Current simulation time
     */
    public static void updateEnvironment(double currentTime) {
        // Update device locations based on mobility models
        for (Map.Entry<Integer, MobilityModel> entry : mobilityModels.entrySet()) {
            int deviceId = entry.getKey();
            MobilityModel mobilityModel = entry.getValue();
            
            // Update device location
            mobilityModel.updateLocation(currentTime);
        }
        
        // Update network conditions based on new device locations and random factors
        updateNetworkConditions();
    }
    
    /**
     * Update network conditions based on current device positions and environmental factors
     */
    private static void updateNetworkConditions() {
        // For each network link, update latency and bandwidth based on:
        // 1. Distance between devices (derived from mobility models)
        // 2. Random factors (network congestion, interference)
        
        for (String linkKey : networkLatencies.keySet()) {
            // Parse device IDs from the link key
            String[] ids = linkKey.split("-");
            int deviceId1 = Integer.parseInt(ids[0]);
            int deviceId2 = Integer.parseInt(ids[1]);
            
            // Get current positions of devices
            MobilityModel mobilityModel = mobilityModels.get(deviceId1);
            if (mobilityModel != null) {
                // Calculate distance factor
                double distanceFactor = 1.0 + random.nextDouble() * 0.5; // 1.0 to 1.5
                
                // Network congestion factor (random)
                double congestionFactor = 1.0 + random.nextDouble() * (random.nextBoolean() ? 0.2 : -0.1); // 0.9 to 1.2
                
                // Update latency
                double baseLatency = networkLatencies.get(linkKey);
                double newLatency = baseLatency * distanceFactor * congestionFactor;
                networkLatencies.put(linkKey, newLatency);
                
                // Update bandwidth (inversely affected by factors)
                double baseBandwidth = networkBandwidths.get(linkKey);
                double newBandwidth = baseBandwidth / (distanceFactor * congestionFactor);
                networkBandwidths.put(linkKey, newBandwidth);
            }
        }
    }
    
    /**
     * Get the current latency between two devices
     * @param sourceId Source device ID
     * @param destId Destination device ID
     * @return Current network latency in milliseconds
     */
    public static double getNetworkLatency(int sourceId, int destId) {
        String linkKey = sourceId + "-" + destId;
        if (networkLatencies.containsKey(linkKey)) {
            return networkLatencies.get(linkKey);
        } else {
            // Default latency if link not found
            return 20.0; // 20ms default
        }
    }
    
    /**
     * Get the current bandwidth between two devices
     * @param sourceId Source device ID
     * @param destId Destination device ID
     * @return Current bandwidth in Mbps
     */
    public static double getNetworkBandwidth(int sourceId, int destId) {
        String linkKey = sourceId + "-" + destId;
        if (networkBandwidths.containsKey(linkKey)) {
            return networkBandwidths.get(linkKey);
        } else {
            // Default bandwidth if link not found
            return 10.0; // 10 Mbps default
        }
    }
    
    /**
     * Predict future network conditions based on historical data and trends
     * Used by DRL agent for better decision-making
     * @param sourceId Source device ID
     * @param destId Destination device ID
     * @param lookaheadTime Time in the future to predict for
     * @return Predicted latency
     */
    public static double predictFutureLatency(int sourceId, int destId, double lookaheadTime) {
        // Current latency
        double currentLatency = getNetworkLatency(sourceId, destId);
        
        // Apply prediction model (simple linear trend prediction in this case)
        // In a real implementation, this would use more sophisticated methods like
        // time series analysis or machine learning models
        double trendFactor = 1.0 + (random.nextDouble() * 0.2 - 0.1); // Random trend between -10% and +10%
        
        return currentLatency * trendFactor;
    }
    
    /**
     * Calculate transmission time for a given data size over a network link
     * @param sourceId Source device ID
     * @param destId Destination device ID
     * @param dataSizeKB Data size in KB
     * @return Transmission time in milliseconds
     */
    public static double calculateTransmissionTime(int sourceId, int destId, double dataSizeKB) {
        // Get current bandwidth in Mbps
        double bandwidthMbps = getNetworkBandwidth(sourceId, destId);
        
        // Convert data size from KB to Mb
        double dataSizeMb = dataSizeKB * 8.0 / 1024.0;
        
        // Calculate transmission time in seconds
        double transmissionTimeSec = dataSizeMb / bandwidthMbps;
        
        // Add network latency
        double latencyMs = getNetworkLatency(sourceId, destId);
        
        // Convert transmission time to milliseconds and add latency
        return (transmissionTimeSec * 1000.0) + latencyMs;
    }
    
    /**
     * Estimate energy consumption for transmitting data over a network link
     * @param sourceId Source device ID
     * @param destId Destination device ID
     * @param dataSizeKB Data size in KB
     * @return Energy consumption in Joules
     */
    public static double estimateTransmissionEnergy(int sourceId, int destId, double dataSizeKB) {
        // Energy model parameters (based on typical mobile device radio)
        double energyPerKB = 0.05; // Joules per KB
        double baseTxCost = 0.1;   // Base cost for transmission
        
        // Calculate transmission energy
        return baseTxCost + (dataSizeKB * energyPerKB);
    }
    
    /**
     * Attach a sensor to the closest edge server based on current mobility
     * @param sensor Sensor to attach
     * @param edgeServers Available edge servers
     */
    public static void attachToClosestEdgeServer(Sensor sensor, List<FogDevice> edgeServers) {
        int mobileDeviceId = sensor.getGatewayDeviceId();
        MobilityModel mobilityModel = mobilityModels.get(mobileDeviceId);
        
        if (mobilityModel != null) {
            // Find closest edge server
            int closestServerId = -1;
            double minLatency = Double.MAX_VALUE;
            
            for (FogDevice server : edgeServers) {
                double latency = getNetworkLatency(mobileDeviceId, server.getId());
                if (latency < minLatency) {
                    minLatency = latency;
                    closestServerId = server.getId();
                }
            }
            
            if (closestServerId != -1) {
                // Update sensor's gateway device
                sensor.setGatewayDeviceId(closestServerId);
                System.out.println("Sensor " + sensor.getName() + " attached to server with ID: " + closestServerId);
            }
        }
    }
}
