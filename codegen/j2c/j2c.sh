#!/bin/bash

if [[ $# != 2 ]]; then
    echo "usage: run.sh <jar file path> <class name>"
    exit 1
fi

JARS="./target/blazeCodeGen-0.0.0.jar"
JARS="${JARS}:${BLAZE_HOME}/accrdd/target/blaze-1.0-SNAPSHOT.jar"
JARS="${JARS}:${BLAZE_HOME}/aparapi_blaze/com.amd.aparapi/dist/aparapi.jar"

scala	-classpath ${JARS} BlazeCodeGen $1 $2

# Example: /curr/cody/Spark_ACC/acc_runtime/examples/kmeans/app/target/sparkkmeans-0.0.0.jar KMeansClassified

