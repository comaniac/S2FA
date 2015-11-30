#define MAX_DATA_SIZE	262144

void run_kernel(
      int N, 
      int * blazeOut, 
      int * in,  int* b_centers, int b_centers__javaArrayLength) {

   int this_centers[3];
#pragma HLS array_partition variable=this_centers complete dim=1
   memcpy((void *) this_centers, (const void *) b_centers, sizeof(int) * 3);

   int this_in[MAX_DATA_SIZE];
#pragma HLS array_partition variable=this_in cyclic factor=4 dim=1
   memcpy((void *) this_in, (const void *) in, sizeof(int) * N);

   int this_out[MAX_DATA_SIZE];
#pragma HLS array_partition variable=this_out cyclic factor=4 dim=1

	 int idx, ii;
   for (ii = 0; ii < N; ii += MAX_DATA_SIZE) {
	   for (idx = 0; idx < MAX_DATA_SIZE; idx++) {
#pragma HLS pipeline II=1
#pragma HLS unroll factor=4

	       int* centers = this_centers;
	       int closest_center = -1;
	       int closest_center_dist = -1;
	       int dist[3];
#pragma HLS array_partition variable=dist complete dim=1

	       int i;
	       for (i = 0; i < 3; i = i + 1) {
#pragma ACCEL parallel factor=3
            dist[i] = abs(centers[i] - this_in[ii + idx]);
	       }
         for (i = 0; i < 3; i = i + 1) {
#pragma ACCEL pipeline
            if (closest_center==-1 || dist[i]<closest_center_dist){
               closest_center = i;
               closest_center_dist = dist[i];
            }
         }
         this_out[ii + idx] = closest_center;
      }
   }
   memcpy((void *) blazeOut, (const void *) this_out, sizeof(int) * N);
}
