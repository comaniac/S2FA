#include <stdio.h>    
#include <stdlib.h>   
#include <math.h>     

#define DATA_SIZE	262144

void run_kernel(
      int N,
      int * blazeOut,
      int * in_1,
      int * in_2);

int main(int argc, char *argv[]) {

	int num_data = DATA_SIZE;
	int *data_1;
	int *data_2;
	int data_length = num_data;

	int output_length = num_data;
	int *output;
	int i, j;

	cmost_malloc(&data_1, data_length * 4);
	cmost_malloc(&data_2, data_length * 4);

	for (i = 0; i < DATA_SIZE; i++) {
		data_1[i] = i;
		data_2[i] = i + 1;
	}

	cmost_malloc(&output, output_length * 4);

#pragma cmost task name="run"
	run_kernel(num_data, output, data_1, data_2);

	fprintf(stderr, "first result: %d\n", output[0]);
	fprintf(stderr, "all done\n");


   return 0;
}
