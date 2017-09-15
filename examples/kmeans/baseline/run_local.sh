#!/bin/bash

spark-submit --class SparkKMeans \
             --driver-memory 4G \
             --executor-memory 4G \
             --master local[*] \
             target/sparkkmeans-0.0.0.jar \
             small.csv 3 0.1

