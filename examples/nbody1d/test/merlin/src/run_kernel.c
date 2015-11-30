#define MAX_DATA_SIZE 1024

void run_kernel(
      int N, 
      float* blazeOut, 
      int blazeOut__javaItemLength, 
      float * body_1, float * body_2,
			float * bodies, int bodies__javaArrayLength) {

	 float local_bodies[MAX_DATA_SIZE];
#pragma HLS array_partition variable=local_bodies cyclic factor=16 dim=1
   memcpy((void *) local_bodies, (const void *) bodies, sizeof(float) * bodies__javaArrayLength);

	 float local_body_1[MAX_DATA_SIZE];
#pragma HLS array_partition variable=local_body_1 cyclic factor=16 dim=1
   memcpy((void *) local_body_1, (const void *) body_1, sizeof(float) * N);

	 float local_body_2[MAX_DATA_SIZE];
#pragma HLS array_partition variable=local_body_2 cyclic factor=16 dim=1
   memcpy((void *) local_body_2, (const void *) body_2, sizeof(float) * N);

   float this_out[MAX_DATA_SIZE][2];
#pragma HLS array_partition variable=this_out cyclic factor=16 dim=1
#pragma HLS array_partition variable=this_out complete dim=2

	 int ii, idx;
   for (ii = 0; ii < N; ii += MAX_DATA_SIZE) {
			for (idx = 0; idx < MAX_DATA_SIZE; idx++) {
#pragma HLS pipeline II=1
#pragma HLS unroll factor=8

				float* all_bodies = local_bodies;
				float this_body_1 = local_body_1[ii + idx];
				float this_body_2 = local_body_2[ii + idx];
	      int body_num = bodies__javaArrayLength;
				float this_acc = 0.0f;
	      int i;
	      for (i = 0; i < body_num; i += 32) {
#pragma HLS pipeline II=1

			   	float acc_buf[32];
#pragma HLS array_partition variable=acc_buf complete dim=1

					int j;
					for (j = 0; j < 32; j++) {
#pragma HLS pipeline II=1
#pragma HLS unroll
						if (j < body_num) {
		          float cur_body = all_bodies[i + j];
		          float dx = cur_body - this_body_1;
		          if (dx!=(float)0){
		             float distSqr = dx * dx;
		             float distSixth = (distSqr * distSqr) * distSqr;
		             float dist = 1.0f / (float)sqrt((float)distSixth);
		             float s = 5.0f * dist;
		             acc_buf[j] = s * dx;
		          }
							else
								acc_buf[j] = 0.0f;
						}
						else
							acc_buf[j] = 0.0f;
					}
					acc_buf[0]  += acc_buf[1];
					acc_buf[2]  += acc_buf[3];
					acc_buf[4]  += acc_buf[5];
					acc_buf[6]  += acc_buf[7];
					acc_buf[8]  += acc_buf[9];
					acc_buf[10] += acc_buf[11];
					acc_buf[12] += acc_buf[13];
					acc_buf[14] += acc_buf[15];
					acc_buf[16] += acc_buf[17];
					acc_buf[18] += acc_buf[19];
					acc_buf[20] += acc_buf[21];
					acc_buf[22] += acc_buf[23];
					acc_buf[24] += acc_buf[25];
					acc_buf[26] += acc_buf[27];
					acc_buf[28] += acc_buf[29];
					acc_buf[30] += acc_buf[31];

					acc_buf[0]  += acc_buf[2];
					acc_buf[4]  += acc_buf[6];
					acc_buf[8]  += acc_buf[10];
					acc_buf[12] += acc_buf[14];
					acc_buf[16] += acc_buf[18];
					acc_buf[20] += acc_buf[22];
					acc_buf[24] += acc_buf[26];
					acc_buf[28] += acc_buf[30];

					acc_buf[0]  += acc_buf[4];
					acc_buf[8]  += acc_buf[12];
					acc_buf[16] += acc_buf[20];
					acc_buf[24] += acc_buf[28];

					acc_buf[0]  += acc_buf[8];
					acc_buf[16]  += acc_buf[24];

					acc_buf[0] += acc_buf[16];
					this_acc += acc_buf[0];
        }
      	this_acc = this_acc * 0.005f;
      	this_out[ii + idx][0]  = (this_body_1 + (this_body_2 * 0.005f)) + ((this_acc * 0.5f) * 0.005f);
	      this_out[ii + idx][1]  = this_body_2 + this_acc;	
			}
   }
   memcpy((void *) blazeOut, (const void *) this_out, sizeof(float) * N * blazeOut__javaItemLength);
}
