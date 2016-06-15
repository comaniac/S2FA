#!/bin/bash

if [[ $# != 4 ]]; then
    echo "usage: j2fa.sh <source file path> <jar file path> <class name> <output file path>"
    exit 1
fi

JARS="/curr/cody/.m2/repository/org/apache/j2fa/j2fa_core/0.0.0/j2fa_core-0.0.0.jar"
JARS="${JARS}:${BLAZE_HOME}/accrdd/target/blaze-1.0.jar"

scala -classpath ${JARS} org.apache.j2fa.J2FA $1 $2 4 $3 $4

