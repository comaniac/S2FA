#!/bin/bash

spark-submit --class TestApp$1 \
	--jars ${BLAZE_HOME/accrdd/target/BLAZE_RUNTIME-1.0-SNAPSHOT.jar \
	--master local[*] \
	target/testapp-0.0.0.jar


