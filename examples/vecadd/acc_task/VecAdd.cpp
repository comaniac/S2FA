#include <stdio.h>
#include <stdlib.h>
#include <sys/time.h>

#include "blaze.h" 
#include "OpenCLEnv.h"

using namespace blaze;

class VecAdd : public Task {
public:

  // extends the base class constructor
  // to indicate how many input blocks
  // are required
  VecAdd(): Task(2) {;}

  // overwrites the compute function
  virtual void compute() {

		struct timeval t1, t2, tr;

		try {
			OpenCLEnv* ocl_env = (OpenCLEnv*)getEnv();

			cl_context       context = ocl_env->getContext();
			cl_kernel        kernel  = ocl_env->getKernel();
			cl_command_queue command = ocl_env->getCmdQueue();

			int err;
			cl_event event;

	    // get input data length
	    int data_length = getInputLength(0);

	    // get the pointer to input/output data
			cl_mem data_1 = *((cl_mem*)getInput(0));
			cl_mem data_2 = *((cl_mem*)getInput(1));

			cl_mem out = *((cl_mem*)getOutput(0, 1, data_length, sizeof(int)));
			if (!data_1 || !data_2 || !out)
			  throw std::runtime_error("Buffer are not allocated");

			err  = clSetKernelArg(kernel, 0, sizeof(int), &data_length);
			err |= clSetKernelArg(kernel, 1, sizeof(cl_mem), &out);
			err |= clSetKernelArg(kernel, 2, sizeof(cl_mem), &data_1);
			err |= clSetKernelArg(kernel, 3, sizeof(cl_mem), &data_2);

			if (err != CL_SUCCESS)
				throw std::runtime_error("Failed to set args!");
			
			size_t global = 1;
			size_t local = 1; 

			gettimeofday(&t1, NULL);

			err = clEnqueueTask(command, kernel, 0, NULL, &event);
			clWaitForEvents(1, &event);

			gettimeofday(&t2, NULL);
			timersub(&t1, &t2, &tr);
			fprintf(stdout, "FPGA execution takes %.4f ms\n",
	    		fabs(tr.tv_sec*1000.0+(double)tr.tv_usec/1000.0));
		}
		catch (std::runtime_error &e) {
			throw e;
		}
    // if there is any error, throw exceptions
  }
};

extern "C" Task* create() {
  return new VecAdd();
}

extern "C" void destroy(Task* p) {
  delete p;
}
