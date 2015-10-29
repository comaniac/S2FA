#include <stdio.h>
#include <stdlib.h>
#include <sys/time.h>

#include "blaze.h" 
#include "OpenCLEnv.h"

using namespace blaze;

class BlackScholes : public Task {
public:

  // extends the base class constructor
  // to indicate how many input blocks
  // are required
  BlackScholes(): Task(1) {;}

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
			int item_length = 2;

	    // get the pointer to input/output data
			cl_mem data = *((cl_mem*)getInput(0));
			cl_mem out = *((cl_mem*)getOutput(0, item_length, data_length, sizeof(float) * item_length));
			if (!data || !out)
			  throw std::runtime_error("Buffer are not allocated");

			err  = clSetKernelArg(kernel, 0, sizeof(int), &data_length);
			err |= clSetKernelArg(kernel, 1, sizeof(cl_mem), &out);
			err |= clSetKernelArg(kernel, 2, sizeof(int), &item_length);
			err |= clSetKernelArg(kernel, 3, sizeof(cl_mem), &data);
			if (err != CL_SUCCESS)
				throw std::runtime_error("Failed to set args!");
			
			size_t global = 1;
			size_t local = 1; 

			gettimeofday(&t1, NULL);

			err = clEnqueueNDRangeKernel(command, kernel, 1, NULL,
			   (size_t *) &global, (size_t *) &local, 0, NULL, &event);
			clWaitForEvents(1, &event);

			gettimeofday(&t2, NULL);
			timersub(&t1, &t2, &tr);
			fprintf(stdout, "FPGA execution takes %.4f ms\n",
	    		fabs(tr.tv_sec*1000.0+(double)tr.tv_usec/1000.0));
/*
			int *sampled_out;
			sampled_out = (int *)malloc(sizeof(int) * data_length);
			err = clEnqueueReadBuffer(command, out, CL_TRUE, 0, sizeof(int) * data_length, 
				sampled_out, 0, NULL, &event);
			int count = 0;
			for (int i = 0; i < data_length; i++)
				if (sampled_out[i])
					count++;
			fprintf(stderr, "PI = %f\n", 4.0 * count / data_length);
			free(sampled_out);
*/
		}
		catch (std::runtime_error &e) {
			throw e;
		}
    // if there is any error, throw exceptions
  }
};

extern "C" Task* create() {
  return new BlackScholes();
}

extern "C" void destroy(Task* p) {
  delete p;
}
