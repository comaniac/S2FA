typedef struct This_s{
   __global float *val$sum;
   __global float *val$a;
   __global float *val$b;
   int passid;
}This;
int get_pass_id(This *this){
   return this->passid;
}
__kernel void run(
   __global float *val$sum, 
   __global float *val$a, 
   __global float *val$b, 
   int passid
){
   This thisStruct;
   This* this=&thisStruct;
   this->val$sum = val$sum;
   this->val$a = val$a;
   this->val$b = val$b;
   this->passid = passid;
   {
      int gid = get_global_id(0);
      this->val$sum[gid]  = this->val$a[gid] + this->val$b[gid];
      return;
   }
}
