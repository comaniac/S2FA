#!/bin/bash

if [[ $# != 3 ]]; then
    echo "usage: j2fa.sh <source file path> <jar file path> <class name>"
    exit 1
fi

SRC_FILE=$1
USER_JARS=$2
KERNEL_NAME=$3
PRJ_DIR=$KERNEL_NAME
CPP_FILE=${3}.cpp
MERLIN_FILE=${3}.c

if [ -d $PRJ_DIR ]; then
	rm -rf $PRJ_DIR
fi
mkdir $PRJ_DIR

JARS="/curr/cody/.m2/repository/org/apache/j2fa/j2fa_core/0.0.0/j2fa_core-0.0.0.jar"
JARS="${JARS}:${BLAZE_HOME}/accrdd/target/blaze-1.0.jar"

echo "Compiling $SRC_FILE to $CPP_FILE"
scala -classpath ${JARS} org.apache.j2fa.J2FA $SRC_FILE $USER_JARS 4 $KERNEL_NAME $CPP_FILE

echo "Testing $CPP_FILE"
g++ -o /dev/null -c $CPP_FILE 2>> g++.log
if [ $? != 0 ]; then
	echo "Failed to generate compilable C++ kernel code. Find g++.log for details."
	mv $CPP_FILE g++.log $PRJ_DIR
	exit 1
fi

echo "Transforming $CPP_FILE to $MERLIN_FILE"
if [ -f "rose_succed" ]; then
	rm rose_succeed
fi
../opt/mars_opt/bin/mars_opt $CPP_FILE -e c -p j2fa
if ! [ -f "rose_succeed" ]; then
	echo "Failed to transform ${CPP_FILE}. Find rose.log for details."
	mv $CPP_FILE g++.log rose* $PRJ_DIR
	exit 1
fi
rm rose_succeed

mv rose_${CPP_FILE} $MERLIN_FILE
gcc -o /dev/null -std=c99 -c $MERLIN_FILE 2>> gcc.log
if [ $? != 0 ]; then
	echo "Failed to generate compilable C code for the Merlin. Find gcc.log for details."
	mv $CPP_FILE g++.log gcc.log rose* $MERLIN_FILE $PRJ_DIR
	exit 1
fi
mv $CPP_FILE g++.log gcc.log rose.log $MERLIN_FILE $PRJ_DIR

