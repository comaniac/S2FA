#define DATA_SIZE	262144

void run_kernel(
      int N, 
      int * blazeOut, 
      int * in_1,  int * in_2) {

   int idx = 0, i;
   for (idx = 0; idx < N; idx += DATA_SIZE) {
      int in_1_buf[DATA_SIZE];
#pragma HLS array_partition variable=in_1_buf cyclic factor=4
      memcpy((void *) in_1_buf, (const void *) &in_1[idx], sizeof(float) * DATA_SIZE);

      int in_2_buf[DATA_SIZE];
#pragma HLS array_partition variable=in_2_buf cyclic factor=4
      memcpy((void *) in_2_buf, (const void *) &in_2[idx], sizeof(float) * DATA_SIZE);

      int blazeOut_buf[DATA_SIZE];
#pragma HLS array_partition variable=blazeOut_buf cyclic factor=4

      for (i = 0; i < DATA_SIZE; i++) {
#pragma ACCEL pipeline_parallel factor=4
	       blazeOut_buf[i] = in_1_buf[i] + in_2_buf[i];
      }
      memcpy((void *) &blazeOut[idx], (const void *) blazeOut_buf, sizeof(float) * DATA_SIZE);
   }
}
