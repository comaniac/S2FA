MANAGER_DIR=../../../manager
PLATFORM_DIR=../../../platforms/xil_ocl
include ../../../Makefile.config

# check all variables
ifeq ($(XILINX_OPENCL_DIR),)
$(error XILINK_OPENCL_DIR not set properly in Makefile.config)
endif
ifeq ($(BOOST_DIR),)
$(error BOOST_DIR not set properly in Makefile.config)
endif
ifeq ($(PROTOBUF_DIR),)
$(error PROTOBUF_DIR not set properly in Makefile.config)
endif
PP=g++
CC=gcc

CFLAGS= -shared -fPIC -std=c++0x -m64 -g 
COMPILE= $(CFLAGS) \
				 -I$(MANAGER_DIR)/include \
				 -I$(BOOST_DIR)/include \
				 -I$(PROTOBUF_DIR)/include \
				 -I$(PLATFORM_DIR) \
				 -I$(XILINX_OPENCL_DIR)/runtime/include/1_2

LINK= -L$(XILINX_OPENCL_DIR)/runtime/lib/x86_64 -lxilinxopencl

ACC = VecAdd

OBJS= $(addsuffix .so, $(addprefix ../, $(ACC)))

all: $(OBJS)

../%.so: %.cpp Makefile
	$(PP) $(COMPILE) $< -o $@

clean:
	rm -rf $(OBJS)
