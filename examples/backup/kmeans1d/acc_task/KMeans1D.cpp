#include <stdio.h>
#include <stdlib.h>
#include <sys/time.h>

#include "blaze.h" 
#include "OpenCLEnv.h"

using namespace blaze;

class KMeans1D : public Task {
public:

  // extends the base class constructor
  // to indicate how many input blocks
  // are required
  KMeans1D(): Task(2) {;}

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
	    int	center_length = getInputLength(1);

			cl_mem 	buf_data 		= *((cl_mem*)getInput(0));
			cl_mem	buf_centers = *((cl_mem*)getInput(1));
	    if (!buf_data || !buf_centers) {
				fprintf(stderr, "Cannot get data pointers\n");
	      throw std::runtime_error("Cannot get data pointers");
	    }

	  	// get the pointer to input/output data
			int num_points = data_length;
			int num_centers = center_length;

	    cl_mem closest_centers = *((cl_mem*)getOutput(0, 1, num_points, sizeof(int)));

			err  = clSetKernelArg(kernel, 0, sizeof(int), &num_points);
			err |= clSetKernelArg(kernel, 1, sizeof(cl_mem), &closest_centers);
			err |= clSetKernelArg(kernel, 2, sizeof(cl_mem), &buf_data);
			err |= clSetKernelArg(kernel, 3, sizeof(cl_mem), &buf_centers);
			err |= clSetKernelArg(kernel, 4, sizeof(int), &center_length);
			if (err != CL_SUCCESS) {
			  throw std::runtime_error("Failed to set gradients!");
			}

			size_t global = 1;
			size_t local = 1; 

			gettimeofday(&t1, NULL);

			err = clEnqueueTask(command, kernel, 0, NULL, &event);
/*
			err = clEnqueueNDRangeKernel(command, kernel, 1, NULL,
			   (size_t *) &global, (size_t *) &local, 0, NULL, &event);
*/
			clWaitForEvents(1, &event);

			gettimeofday(&t2, NULL);
			timersub(&t1, &t2, &tr);
			fprintf(stdout, "FPGA execution takes %.4f ms\n",
	    		fabs(tr.tv_sec*1000.0+(double)tr.tv_usec/1000.0));
		}
		catch (std::runtime_error &e) {
			throw e;
		}
  }
};

extern "C" Task* create() {
  return new KMeans1D();
}

extern "C" void destroy(Task* p) {
  delete p;
}
