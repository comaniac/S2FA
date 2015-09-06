#!/bin/bash

spark-submit --class TestApp$1 \
	--jars ${BLAZE_HOME}/accrdd/target/blaze-1.0-SNAPSHOT.jar,${BLAZE_HOME}/aparapi_blaze/com.amd.aparapi/dist/aparapi.jar \
	--master local[*] \
	target/test-0.0.0.jar


