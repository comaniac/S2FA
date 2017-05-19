#!/bin/bash

rm -rf spark.log

spark-submit --class AES \
        --master local[*] \
        target/aes-0.0.0.jar \
		key.data input.data
