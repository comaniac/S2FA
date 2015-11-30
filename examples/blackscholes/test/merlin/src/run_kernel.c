#include <math.h>
#define MAX_DATA_SIZE 262144

float BlackScholes__phi(float x)
{{
   float absX = (x>0)? x: -x;
   float t = 1.0f / (1.0f + (0.2316419f * absX));
   float y = 1.0f - (((0.3989423f * (float)exp((float)((-x * x) / 2.0f))) * t) * 
						(0.31938154f + (t * (-0.35656378f + (t * (1.7814779f + (t * (-1.8212559f + (t * 1.3302745f)))))))));

   return((x<0.0f)?(1.0f - y):y);
}}

void BlackScholes__call(float data, float* out, int out__javaItemLength)
{{  
   {
      float my_in = data;
      float S = (10.0f * my_in) + (100.0f * (1.0f - my_in));
      float K = (10.0f * my_in) + (100.0f * (1.0f - my_in));
      float T = (1.0f * my_in) + (10.0f * (1.0f - my_in));
      float R = (0.01f * my_in) + (0.05f * (1.0f - my_in));
      float sigmaVal = (0.01f * my_in) + (0.1f * (1.0f - my_in));
      float sigmaSqrtT = sigmaVal * (float)sqrt((float)T);
      float d1 = ((float)log((float)(S / K)) + ((R + ((sigmaVal * sigmaVal) / 2.0f)) * T)) / sigmaSqrtT;
      float d2 = d1 - sigmaSqrtT;
      float KexpMinusRT = K * (float)exp((float)(-R * T));
      float c = (S * BlackScholes__phi(d1)) - (KexpMinusRT * BlackScholes__phi(d2));
      float p = (KexpMinusRT * BlackScholes__phi(-d2)) - (S * BlackScholes__phi(-d1));
      out[0]  = c;
      out[1]  = p;
   };
}}

void run_kernel(
      int N, 
      float * blazeOut, 
      int blazeOut__javaItemLength, 
      float * blazeIn) {

   float this_in[MAX_DATA_SIZE];
#pragma HLS array_partition variable=this_in cyclic factor=2 dim=1 
   memcpy((void *) this_in, (const void *) blazeIn, sizeof(float) * N);

   float this_out[MAX_DATA_SIZE][2];
#pragma HLS array_partition variable=this_out cyclic factor=2 dim=1

	 int idx;
   for (idx = 0; idx < MAX_DATA_SIZE; idx++) {
#pragma ACCEL pipeline_parallel factor=2
//      BlackScholes__call(this_in[idx], this_out[idx], blazeOut__javaItemLength);

      if (idx < N) {
	      float my_in = this_in[idx];
	      float S = (10.0f * my_in) + (100.0f * (1.0f - my_in));
	      float K = (10.0f * my_in) + (100.0f * (1.0f - my_in));
	      float T = (1.0f * my_in) + (10.0f * (1.0f - my_in));
	      float R = (0.01f * my_in) + (0.05f * (1.0f - my_in));
	      float sigmaVal = (0.01f * my_in) + (0.1f * (1.0f - my_in));
	      float sigmaSqrtT = sigmaVal * (float)sqrt((float)T);
	      float d1 = ((float)log((float)(S / K)) + ((R + ((sigmaVal * sigmaVal) / 2.0f)) * T)) / sigmaSqrtT;
	      float d2 = d1 - sigmaSqrtT;
	      float KexpMinusRT = K * (float)exp((float)(-R * T));
	      float c = (S * BlackScholes__phi(d1)) - (KexpMinusRT * BlackScholes__phi(d2));
	      float p = (KexpMinusRT * BlackScholes__phi(-d2)) - (S * BlackScholes__phi(-d1));
	      this_out[idx][0]  = c;
	      this_out[idx][1]  = p;
			}
   }
   memcpy((void *) blazeOut, (const void *) this_out, sizeof(float) * N * blazeOut__javaItemLength);
}
