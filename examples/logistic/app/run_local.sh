#!/bin/bash

DATA=../data

spark-submit --class LR \
    --driver-memory 4G \
    --executor-memory 4G \
    --master local[*] \
    target/lr-0.0.0.jar ${DATA}/train_data.txt 3 10


