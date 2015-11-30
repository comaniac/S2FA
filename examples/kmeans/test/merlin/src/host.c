#include <stdio.h>    
#include <stdlib.h>   
#include <string.h>   

#define ITERATION 3

void run_kernel(
      int N, 
       int * blazeOut, 
       int* in_blazeLocal784, 
      int in_blazeLocal784__javaItemLength, int b_D,  int* b_centers, int b_centers__javaArrayLength);


int main(int argc, char *argv[]) {

	int dims = 784;
	int data_length = 30000 * 784;
	float *data;
	int num_data = data_length / 784;

	float *centers;
	int centers_length = 3 * 784;
	int num_centers = centers_length / 784;

	int output_length = 30000;
	int *output;

	int *fixed_data;
	int *fixed_centers;

	int i, j;

	data = (float *)malloc(sizeof(float) * data_length);
	fixed_data = (int *)malloc(sizeof(int) * data_length);
	centers = (float *)malloc(sizeof(float) * centers_length);
	fixed_centers = (int *)malloc(sizeof(int) * centers_length);
	output = (int *)malloc(sizeof(int) * output_length);

	// Read input data from file
	FILE *infilep = fopen("/curr/diwu/prog/logistic/data/train_data.txt", "r");
	float tmp;
	for (i = 0; i < 30000; ++i) {
		for (j = 0; j < 10; ++j) {
			fscanf(infilep, "%f", &tmp);
		}
		for (j = 0; j < 784; ++j) {
			fscanf(infilep, "%f", &data[i * 784 + j]);
		}
	}
	fclose(infilep);

	// Convert to fixed points
	for (i = 0; i < 23520000; ++i) {
		fixed_data[i] = (int) (data[i] * 1e+3);
	}

	srand(99);
	int c = (int) rand() % (30000 - num_centers);
	fprintf(stderr, "center seed %d\n", c);
	for (i = 0; i < 3; ++i) {
		for (j = 0; j < 784; ++j) {
			centers[i * 784 + j] = data[(c + i) * 784 + j];
		}
		fprintf(stderr, "centers %f\n", centers[i * 784]);
	}

	 int iter;
	 for (iter = 0; iter < ITERATION; ++iter) {
		
		 for (i = 0; i < 2352; ++i) {
			 fixed_centers[i] = (int) (centers[i] * 1e+3);
		 }

#pragma cmost task name="run"
     run_kernel(num_data, output, fixed_data, dims, dims, fixed_centers, centers_length);

		 for (i = 0; i < 2352; ++i) {
		  centers[i] = 0;
		 }

		 int count[3] = {0};
		 for (i = 0; i < 30000; ++i) {
			 int c = output[i];
		 	 count[c]++;
		 	 for (j = 0; j < 784; ++j) {
				 centers[c * 784 + j] += data[i * 784 + j];
			 }
		 }

		 fprintf(stderr, "\n");
		 for (i = 0; i < 3; ++i) {
			 fprintf(stderr, "Center %d (%d) \t", i, count[i]);
			 for (j = 0; j < 784; ++j) {
				 centers[i * 784 + j] /= count[i];
		 	 }
		 }

		 fprintf(stderr, "\n");
	}
 
	fprintf(stderr, "all done\n");
  return 0;
}
