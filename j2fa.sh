#!/bin/bash

if [[ $# != 3 ]]; then
    echo "usage: j2fa.sh <source file path> <jar file path> <class name>"
    exit 1
fi

#JARS="${HOME}/.m2/repository/j2fa/j2fa_core/0.0.0/j2fa_core-0.0.0.jar"
JARS="./core/target/j2fa_core-0.0.0.jar"
JARS="${JARS}:${BLAZE_HOME}/accrdd/target/blaze-1.0-SNAPSHOT.jar"

scala	-classpath ${JARS} org.apache.j2fa.J2FA $1 $2 $3

# Example: /curr/cody/Spark_ACC/acc_runtime/examples/kmeans/app/target/sparkkmeans-0.0.0.jar KMeansClassified

