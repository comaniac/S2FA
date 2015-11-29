__kernel 
void test(__global int *a, __global int *b, __global int *c, int N)
{
	int d[10];
	int *e = &d[2];
//	int e;

	__attribute__((xcl_pipeline_loop))
	for (int i = 0; i < N;) {
		int boko_dup[16];
		__attribute__((opencl_unroll_hint))
		for (int boko_idx = 0; boko_idx < 16; ++boko_idx, ++i) {
			if (i < N)
				boko_dup[boko_idx] = a[i];
			else
				boko_dup[boko_idx] = 0;
		}
		__attribute__((opencl_unroll_hint))
		for (int boko_idx = 0; boko_idx < 16; boko_idx += 2)
			boko_dup[bok_idx] += boko_dup[boko_idx + 1];
	
		__attribute__((opencl_unroll_hint))
		for (int boko_idx = 0; boko_idx < 16; boko_idx += 4)
			boko_dup[bok_idx] += boko_dup[boko_idx + 2];
	
		__attribute__((opencl_unroll_hint))
		for (int boko_idx = 0; boko_idx < 16; boko_idx += 8)
			boko_dup[bok_idx] += boko_dup[boko_idx + 4];
	
		boko_dup[0] += boko_dup[8];
		*e += boko_dup[0];
	/*
	for (int i = 0; i < N; i++) {
		*e = *e + a[i];
	*/
	}

	__attribute__((xcl_pipeline_loop))
	/* Boko Loop with no reduction since loop variable used */
	for (int i = 0; i < N; i++) {
		d[i] = d[i] + 1;
	}
}
