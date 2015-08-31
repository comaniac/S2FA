JAVA_HOME=$HOME/.local/jdk1.7.0_79/
BOOST_DIR=/curr/diwu/tools/boost_1_55_0/install
PROTOBUF_DIR=/curr/diwu/tools/protobuf-2.5.0/build/install
HADOOP_DIR=/curr/diwu/tools/hadoop/hadoop-2.5.2

export BLAZE_HOME=`pwd`
export LD_LIBRARY_PATH=$HADOOP_DIR/lib/native:$LD_LIBRARY_PATH
export LD_LIBRARY_PATH=$JAVA_HOME/jre/lib/amd64/server:$LD_LIBRARY_PATH

