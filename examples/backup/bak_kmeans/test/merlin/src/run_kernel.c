#define DATA_SIZE	40000

/*
int KMeansClassified__call(int b_D, int* b_centers, int b_centers__javaArrayLength, int* in_blazeLocal784, int in_blazeLocal784__javaItemLength)
{
{
   return(
   {
      int* centers_blazeLocalMax4096 = b_centers;
      int D = b_D;
      int K = b_centers__javaArrayLength / D;
      int closest_center = -1;
      int closest_center_dist = -1;
      int dist = 0;
      int i = 0;

      for (i = 0; i < 3; i++) {{
            int j = 0, k;
						dist = 0;
						int sdist[16];
#pragma HLS array_partition variable=sdist complete dim=1

            while (j < 784) {
							for (k = 0; k < 16; k++) {
#pragma HLS unroll
								if (j < 784)
									sdist[k] = abs(centers_blazeLocalMax4096[i * 784 + j] - in_blazeLocal784[j]);
								else
									sdist[k] = 0;
							}
							sdist[0]  += sdist[1];
							sdist[2]  += sdist[3];
							sdist[4]  += sdist[5];
							sdist[6]  += sdist[7];
							sdist[8]  += sdist[9];
							sdist[10] += sdist[11];
							sdist[12] += sdist[13];
							sdist[14] += sdist[15];

							sdist[0]  += sdist[2];
							sdist[4]  += sdist[6];
							sdist[8]  += sdist[10];
							sdist[12] += sdist[14];

							sdist[0]  += sdist[4];
							sdist[8]  += sdist[12];

              dist  = sdist[0] + sdist[8];
							j += 16;
            }
         		if (closest_center==-1 || dist<closest_center_dist){
            	closest_center = i;
            	closest_center_dist = dist;
         		}
         }
      }
/*
      i = 0;
      for (i=0; i < 3; i = i + 1) {
         if (closest_center==-1 || dist[i]<closest_center_dist){
            closest_center = i;
            closest_center_dist = dist[i];
         }
      }

      closest_center;
   });
}
}
 */
void run_kernel(
      int N, 
       int * blazeOut, 
       int* in_blazeLocal784, 
      int in_blazeLocal784__javaItemLength, int b_D,  int* b_centers, int b_centers__javaArrayLength) {

   int centers[4096];
#pragma HLS array_partition variable=centers cyclic factor=16 dim=1
   memcpy((void *) centers, (const void *) b_centers, sizeof(int) * b_centers__javaArrayLength);

   int idx;
   for (idx = 0; idx < DATA_SIZE; idx++) {
#pragma HLS pipeline II=1
#pragma HLS unroll factor=2

      if (idx < N) {
         int this_in[784];
#pragma HLS array_partition variable=this_in cyclic factor=16 dim=1
         memcpy((void *) this_in, (const void *) &in_blazeLocal784[idx * in_blazeLocal784__javaItemLength], sizeof(int) * in_blazeLocal784__javaItemLength);

//      blazeOut[idx] = KMeansClassified__call(b_D, centers, b_centers__javaArrayLength, this_in, in_blazeLocal784__javaItemLength);

         int* centers_blazeLocalMax4096 = centers;
         int closest_center = -1;
         int closest_center_dist = -1;
         int dist = 0;

         int i = 0;
         for (i = 0; i < 3; i++) {
#pragma HLS pipeline
            int j = 0, k;
    				dist = 0;
						int sdist[16];
#pragma HLS array_partition variable=sdist complete dim=1

            while (j < 784) {
							for (k = 0; k < 16; k++) {
#pragma HLS unroll
								if (j < 784)
									sdist[k] = abs(centers_blazeLocalMax4096[i * 784 + j] - this_in[j]);
								else
									sdist[k] = 0;
							}
							sdist[0]  += sdist[1];
							sdist[2]  += sdist[3];
							sdist[4]  += sdist[5];
							sdist[6]  += sdist[7];
							sdist[8]  += sdist[9];
							sdist[10] += sdist[11];
							sdist[12] += sdist[13];
							sdist[14] += sdist[15];

							sdist[0]  += sdist[2];
							sdist[4]  += sdist[6];
							sdist[8]  += sdist[10];
							sdist[12] += sdist[14];

							sdist[0]  += sdist[4];
							sdist[8]  += sdist[12];

              dist  = sdist[0] + sdist[8];
							j += 16;
            }
         		if (closest_center==-1 || dist<closest_center_dist){
            	closest_center = i;
            	closest_center_dist = dist;
         		}
         }
         blazeOut[idx] = closest_center; 
      }
   }
}
