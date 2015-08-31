#!/bin/bash

spark-submit --class LoopBack \
	--jars ${BLAZE_HOME}/accrdd/target/BLAZE_RUNTIME-1.0-SNAPSHOT.jar \
	--master local[*] \
	target/LoopBack-0.0.0.jar $@

