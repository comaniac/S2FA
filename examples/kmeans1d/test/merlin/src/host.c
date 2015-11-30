#include <stdio.h>    
#include <stdlib.h>   
#include <string.h>   

#define ITERATION 3

void run_kernel(
      int N, 
      int * blazeOut, 
      int * in, int * b_centers, int b_centers__javaArrayLength);

int main(int argc, char *argv[]) {

	int data_length = 262144;
	int num_data = data_length;

	int centers_length = 3;
	int num_centers = centers_length;

	int output_length = data_length;
	int *output;

	int *data;
	int *centers;

	int i, j;

	data = (int *)malloc(sizeof(int) * data_length);
	centers = (int *)malloc(sizeof(int) * centers_length);
	output = (int *)malloc(sizeof(int) * output_length);

	srand(99);
	for (i = 0; i < data_length; ++i) {
		data[i] = rand() % 1000;
	}

	int c = (int) rand() % (data_length - num_centers);
	fprintf(stderr, "center seed %d\n", c);
	for (i = 0; i < 3; ++i) {
		centers[i] = data[c + i];
		fprintf(stderr, "centers %d\n", centers[i]);
	}

	 int iter;
	 for (iter = 0; iter < ITERATION; ++iter) {
		
#pragma cmost task name="run"
     run_kernel(num_data, output, data, centers, centers_length);

		 for (i = 0; i < 3; ++i) {
		 		centers[i] = 0;
		 }

		 int count[3] = {0};
		 for (i = 0; i < data_length; ++i) {
			 int c = output[i];
		 	 count[c]++;
			 centers[c] += data[i];
		 }

		 fprintf(stderr, "\n");
		 for (i = 0; i < 3; ++i) {
			 fprintf(stderr, "Center %d (%d) \t", i, count[i]);
			 centers[i] /= count[i];
		 }
		 fprintf(stderr, "\n");
	}
 
	fprintf(stderr, "all done\n");
  return 0;
}
