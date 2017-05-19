#!/bin/bash

rm -rf spark.log

spark-submit --class NW \
        --master local[*] \
        target/nw-0.0.0.jar \
		input.data
