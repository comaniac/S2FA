#!/bin/bash

DATA=../
rm -rf spark.log
JARS="${BLAZE_HOME}/accrdd/target/blaze-1.0.jar"

spark-submit --class PageRank \
             --driver-memory 32G \
             --executor-memory 32G \
             --master local[*] \
             --jars ${JARS} \
             target/pagerank-0.0.0.jar \
             run 3 ${DATA}/converted_data ${DATA}/converted_doc
