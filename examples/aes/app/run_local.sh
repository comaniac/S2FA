#!/bin/bash

DATA=../data

rm -rf spark.log

spark-submit --class AES \
        --master local[*] \
        target/aes-0.0.0.jar \
		${DATA}/key.data ${DATA}/input.data
