#!/bin/bash

JARS="${BLAZE_HOME}/accrdd/target/blaze-1.0.jar"
DATA=../data

rm -rf spark.log

spark-submit --class AES \
        --master local[*] \
        --jars ${JARS} \
        target/aes-0.0.0.jar \
		${DATA}/key.data ${DATA}/input.data
