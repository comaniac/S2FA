#!/bin/bash

DIR=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
source $DIR/Makefile.config

export BLAZE_HOME=$DIR
export LD_LIBRARY_PATH=$BOOST_DIR/lib:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH=$PROTOBUF_DIR/lib:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH=$HADOOP_DIR/lib/native:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH=$JAVA_HOME/jre/lib/amd64/server:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH=$BLAZE_HOME/manager/lib:$LD_LIBRARY_PATH

