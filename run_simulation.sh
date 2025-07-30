#!/bin/bash

# Change to the project directory
cd /home/wexa/CascadeProjects/DRL-TaskOffloading-MEC

# Set Java logging properties to disable logs
JAVA_OPTS="-Djava.util.logging.config.file=/dev/null"
JAVA_OPTS="${JAVA_OPTS} -Dorg.cloudsim.cloudsim.core.CloudSim.PRINT=false"

# Run the simulation with logging disabled
java ${JAVA_OPTS} -cp classes:iFogSim/jars/*:iFogSim/jars/cloudsim-3.0.3.jar:iFogSim/jars/cloudsim-examples-3.0.3.jar:iFogSim/jars/commons-math3-3.5/commons-math3-3.5.jar org.fog.test.drl.LearnableDRLTaskOffloading > results.txt

# Display only the results section
echo ""
echo "Displaying only simulation results..."
echo ""
# Use awk to extract and display only the results section from the output file
awk '/Simulation completed/{flag=1} flag' results.txt

# Clean up
rm results.txt
