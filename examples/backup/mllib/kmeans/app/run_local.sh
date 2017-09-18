#!/bin/bash

if [[ $# != 2 ]]; then
    echo usage: run.sh niters npartitions
    exit 1
fi

/curr/cody/spark-1.5.1_customized/bin/spark-submit --class KMeansApp \
				--driver-memory 4G \
				--executor-memory 4G \
        --master local[*] \
        target/kmeansapp-0.0.0.jar \
        3 $1 $2 block_1.csv

#/curr/cody/test/testInput3.txt

#hdfs://cdsc0:9000/user/cody/kmeans_input_small.txt
