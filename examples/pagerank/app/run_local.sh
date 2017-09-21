#!/bin/bash

DATA=../
rm -rf spark.log

spark-submit --class PageRank \
             --driver-memory 32G \
             --executor-memory 32G \
             --master local[*] \
             target/pagerank-0.0.0.jar \
             3 ${DATA}/converted_data ${DATA}/converted_doc
