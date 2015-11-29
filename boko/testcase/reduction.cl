__kernel 
void test(__global int *a, __global int *b, __global int *c, int N)
{
	int d[10];
	int *e = &d[2];
//	int e;

	for (int i = 0; i < N; i++) {
		*e = *e + a[i];
	}

	for (int i = 0; i < N; i++) {
		d[i] = d[i] + 1;
	}
}
