#!/bin/bash

if [[ $# != 2 ]]; then
    echo "usage: j2fa.sh <jar file path> <class name>"
    exit 1
fi

JARS="./core/target/j2fa_core-0.0.0.jar"
JARS="${JARS}:./aparapi/target/aparapi-1.0.0.jar"
JARS="${JARS}:${BLAZE_HOME}/accrdd/target/blaze-1.0-SNAPSHOT.jar"

scala	-classpath ${JARS} org.apache.j2fa.J2FA $1 $2 y

# Example: /curr/cody/Spark_ACC/acc_runtime/examples/kmeans/app/target/sparkkmeans-0.0.0.jar KMeansClassified

