java \
 -Djava.library.path=../../com.amd.aparapi.jni/dist \
 -Dcom.amd.aparapi.executionPhase=$1 \
 -Dcom.amd.aparapi.kernelFileName=$2 \
 -Dcom.amd.aparapi.executionMode=$3 \
 -Dcom.amd.aparapi.enableExecutionPhaseReporting=true \
 -Dcom.amd.aparapi.enableExecutionModeReporting=true \
 -classpath ../../com.amd.aparapi/dist/aparapi.jar:add.jar \
 com.amd.aparapi.sample.add.Main


# -Dcom.amd.aparapi.enableVerboseJNI=true \
