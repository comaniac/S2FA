spark-submit \
    --class GenerateInput \
    --master local[*] \
    target/pagerank-0.0.0.jar \
    data 10 21000 1000 500 doc.txt

#	data 10 5 700 500 doc.txt
