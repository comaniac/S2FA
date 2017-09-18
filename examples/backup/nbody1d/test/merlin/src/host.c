#include <stdio.h>    
#include <stdlib.h>   
#include <string.h>   
#include <math.h>     

void run_kernel(
      int N, 
      float* blazeOut, 
      int blazeOut__javaItemLength, 
      float * this_body_1, float * this_body_2,
			float * bodies, int bodies__javaArrayLength);

int main(int argc, char *argv[]) {

	int num_data = 1024;
	int data_length = num_data;

	int output_length = data_length;

	float *data;
	float *acc;
	float *output;
	int i;
	srand(0);

	data = (float *)malloc(sizeof(float) * data_length);
	acc = (float *)malloc(sizeof(float) * data_length);
	output = (float *)malloc(sizeof(float) * output_length);

	for (i = 0; i < 1024; i++) {
		float theta = ((float) rand() / RAND_MAX) * 3.1415926 * 2;
		float phi = ((float) rand() / RAND_MAX) * 3.1415926 * 2;
		float radius = ((float) rand() / RAND_MAX) * 20;

		data[i] = radius * cos(theta) * sin(phi);
		if (!(i % 2))
			data[i] += 30;
		else
			data[i] -= 30;

		acc[i] = 0;
	}

#pragma cmost task name="run"
	run_kernel(num_data, output, 2, data, acc, data, num_data);

	fprintf(stderr, "first body: %f %f\n", output[0], output[1]);
	fprintf(stderr, "all done\n");

  return 0;
}
