#pragma OPENCL EXTENSION cl_khr_fp64 : enable

typedef struct This_s{
   __global float* b_w;
   int ary_in0_item_length;
   int ary_out_item_length;
   } This;
/* [Blaze CodeGen] WARNING: This method tries to return an array in ByteCode.
   This function must be called by the main function of the kernel, 
   or the kernel might not be synthesized or compiled. */
void LogisticRegression__call(This *this,  __global float* _data_blazeLocal7840, __global float* grad)
{
   __local float data_blazeLocal7840[7840];
   for (int localAryCpy = 0; localAryCpy < 7840; localAryCpy++) {
      data_blazeLocal7840[localAryCpy] = _data_blazeLocal7840[localAryCpy]; }

   for (int localAryCpy = 0; localAryCpy < this->ary_out_item_length; localAryCpy++) {
      grad[localAryCpy] = 0.0f;
	}
{
   
   {
      int _L = 10;
      int _D = 784;
      __local float w_blazeLocal7840[7840];
      for (int localAryCpy = 0; localAryCpy < 7840; localAryCpy++) {
         w_blazeLocal7840[localAryCpy] = this->b_w[localAryCpy]; };

      int i = 0;
      while (i<_L){
         {
            float dot = 0.0f;
            int j = 0;
            for (; j<_D; j = j + 1) {
               dot = dot + (w_blazeLocal7840[((i * _D) + j)] * data_blazeLocal7840[(j + _L)]);
            }
            float c = ((1.0f / (1.0f + (float)exp((double)(-data_blazeLocal7840[i] * dot)))) - 1.0f) * data_blazeLocal7840[i];
            j = 0;
            for (; j<_D; j = j + 1){
               grad[(i * _D) + j]  = grad[((i * _D) + j)] + (c * data_blazeLocal7840[(j + _L)]);
            }
            i = i + 1;
         }
      }
   };
}
}
__kernel __attribute__((reqd_work_group_size(512, 1, 1))) 
void run(
      int N, 
      __global float* ary_in0, 
      int ary_in0_item_length, 
      __global float* ary_out, 
      int ary_out_item_length, __global float* b_w) {
   int nthreads = get_global_size(0);
   int part = (N / nthreads) + 1;
   int idx = get_global_id(0) * part;
   int end = (idx + part > N)? N: idx + part;
   This thisStruct;
   This* this = &thisStruct;
   this->b_w = b_w;
   this->ary_in0_item_length = ary_in0_item_length;
   this->ary_out_item_length = ary_out_item_length;
   for (; idx < end; idx++) {
      LogisticRegression__call(this, &ary_in0[idx * ary_in0_item_length], &ary_out[idx * ary_out_item_length]);
      
   }
}
