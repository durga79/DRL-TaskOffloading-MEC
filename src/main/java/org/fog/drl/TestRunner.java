package org.fog.drl;

import org.cloudbus.cloudsim.Log;

import java.util.HashMap;
import java.util.Map;

/**
 * Test runner to execute multiple simulation scenarios with different configurations
 * and compare their performance
 */
public class TestRunner {
    
    public static void main(String[] args) {
        Log.disable();
        System.out.println("Starting DRL Task Offloading Test Runner");
        System.out.println("=======================================");
        
        try {
            // Results storage
            Map<String, Double> latencyResults = new HashMap<>();
            Map<String, Double> energyResults = new HashMap<>();
            
            // Run simulation with different configurations
            runBasicTest(latencyResults, energyResults);
            
            // Run scalability test
            runScalabilityTest(10, 5); // 10 mobile devices, 5 edge servers
            runScalabilityTest(20, 10); // 20 mobile devices, 10 edge servers
            
            // Run network condition test
            runNetworkConditionTest(10.0); // 10ms edge latency (good condition)
            runNetworkConditionTest(50.0); // 50ms edge latency (poor condition)
            
            // Visualize and save results
            ResultsVisualizer.saveLatencyResults(latencyResults, 
                "Latency comparison across different task offloading strategies");
            ResultsVisualizer.saveEnergyResults(energyResults, 
                "Energy consumption comparison across different task offloading strategies");
            
            System.out.println("\nAll tests completed. Results saved to simulation_results directory.");
            
        } catch (Exception e) {
            System.err.println("Error running tests: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Run a basic test comparing DRL with baseline approaches
     * @param latencyResults Map to store latency results
     * @param energyResults Map to store energy results
     */
    public static void runBasicTest(Map<String, Double> latencyResults, Map<String, Double> energyResults) {
        System.out.println("\nRunning basic comparison test...");
        
        try {
            // Run DRL-based task offloading simulation
            System.out.println("Running DRL-based task offloading simulation...");
            DRLTaskOffloadingEvaluation.main(new String[]{"--drl-only"});
            
            // Extract and store results
            double drlLatency = DRLTaskOffloadingEvaluation.getLatencyResult("DRL");
            double drlEnergy = DRLTaskOffloadingEvaluation.getEnergyResult("DRL");
            
            latencyResults.put("DRL", drlLatency);
            energyResults.put("DRL", drlEnergy);
            
            // Run Cloud-only simulation
            System.out.println("Running Cloud-only task offloading simulation...");
            DRLTaskOffloadingEvaluation.main(new String[]{"--cloud-only"});
            
            // Extract and store results
            double cloudLatency = DRLTaskOffloadingEvaluation.getLatencyResult("CloudOnly");
            double cloudEnergy = DRLTaskOffloadingEvaluation.getEnergyResult("CloudOnly");
            
            latencyResults.put("Cloud-only", cloudLatency);
            energyResults.put("Cloud-only", cloudEnergy);
            
            // Run Edge-only simulation
            System.out.println("Running Edge-only task offloading simulation...");
            DRLTaskOffloadingEvaluation.main(new String[]{"--edge-only"});
            
            // Extract and store results
            double edgeLatency = DRLTaskOffloadingEvaluation.getLatencyResult("EdgeOnly");
            double edgeEnergy = DRLTaskOffloadingEvaluation.getEnergyResult("EdgeOnly");
            
            latencyResults.put("Edge-only", edgeLatency);
            energyResults.put("Edge-only", edgeEnergy);
            
            // Run Mobile-only simulation
            System.out.println("Running Mobile-only task offloading simulation...");
            DRLTaskOffloadingEvaluation.main(new String[]{"--mobile-only"});
            
            // Extract and store results
            double mobileLatency = DRLTaskOffloadingEvaluation.getLatencyResult("MobileOnly");
            double mobileEnergy = DRLTaskOffloadingEvaluation.getEnergyResult("MobileOnly");
            
            latencyResults.put("Mobile-only", mobileLatency);
            energyResults.put("Mobile-only", mobileEnergy);
            
            // Run Greedy heuristic simulation
            System.out.println("Running Greedy heuristic task offloading simulation...");
            DRLTaskOffloadingEvaluation.main(new String[]{"--greedy"});
            
            // Extract and store results
            double greedyLatency = DRLTaskOffloadingEvaluation.getLatencyResult("Greedy");
            double greedyEnergy = DRLTaskOffloadingEvaluation.getEnergyResult("Greedy");
            
            latencyResults.put("Greedy", greedyLatency);
            energyResults.put("Greedy", greedyEnergy);
            
            // Print summary
            System.out.println("\nBasic Test Results Summary:");
            System.out.println("---------------------------");
            System.out.printf("DRL-based     : Latency = %.2f ms, Energy = %.2f J\n", drlLatency, drlEnergy);
            System.out.printf("Cloud-only    : Latency = %.2f ms, Energy = %.2f J\n", cloudLatency, cloudEnergy);
            System.out.printf("Edge-only     : Latency = %.2f ms, Energy = %.2f J\n", edgeLatency, edgeEnergy);
            System.out.printf("Mobile-only   : Latency = %.2f ms, Energy = %.2f J\n", mobileLatency, mobileEnergy);
            System.out.printf("Greedy        : Latency = %.2f ms, Energy = %.2f J\n", greedyLatency, greedyEnergy);
            
        } catch (Exception e) {
            System.err.println("Error in basic test: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Run scalability test with varying number of devices
     * @param numMobileDevices Number of mobile devices
     * @param numEdgeServers Number of edge servers
     */
    public static void runScalabilityTest(int numMobileDevices, int numEdgeServers) {
        System.out.println("\nRunning scalability test with " + numMobileDevices + 
                          " mobile devices and " + numEdgeServers + " edge servers...");
        
        try {
            // Construct command line arguments for scalability test
            String[] args = new String[]{
                "--all",
                "--edge-servers", String.valueOf(numEdgeServers),
                "--mobile-devices", String.valueOf(numMobileDevices)
            };
            
            // Run simulation with specified configuration
            DRLTaskOffloadingEvaluation.main(args);
            
            // Results will be saved by the evaluation class
            
            System.out.println("Scalability test completed for configuration: " + 
                              numMobileDevices + " mobile devices, " + numEdgeServers + " edge servers");
            
        } catch (Exception e) {
            System.err.println("Error in scalability test: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Run network condition test with varying edge latency
     * @param edgeLatency Edge network latency in ms
     */
    public static void runNetworkConditionTest(double edgeLatency) {
        System.out.println("\nRunning network condition test with edge latency = " + 
                          edgeLatency + " ms...");
        
        try {
            // Construct command line arguments for network condition test
            String[] args = new String[]{
                "--all",
                "--edge-latency", String.valueOf(edgeLatency)
            };
            
            // Run simulation with specified configuration
            DRLTaskOffloadingEvaluation.main(args);
            
            // Results will be saved by the evaluation class
            
            System.out.println("Network condition test completed for edge latency = " + edgeLatency + " ms");
            
        } catch (Exception e) {
            System.err.println("Error in network condition test: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
