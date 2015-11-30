#include <stdio.h>    
#include <stdlib.h>   
#include <math.h>     

void run_kernel(
      int N,
      float * blazeOut,
      int blazeOut__javaItemLength,
      float * blazeIn);

int main(int argc, char *argv[]) {

	int num_data = 65536;
	float *data;
	int item_length = 2;
	int data_length = num_data;

	int output_length = num_data * item_length;
	float *output;
	int i, j;

	data = (float *)malloc(sizeof(float) * data_length);
	for (i = 0; i < num_data; i++) {
		data[i] = i * 1.0 / num_data;
	}

	output = (float *)malloc(sizeof(float) * output_length);

#pragma cmost task name="run"
	run_kernel(num_data, output, 2, data);

	fprintf(stderr, "first result: %f %f\n", output[0], output[1]);
	fprintf(stderr, "all done\n");


   return 0;
}
