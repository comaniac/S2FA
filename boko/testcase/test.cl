__kernel run(__global int *in, __global int *out) {
	int count = 0;

	for (int i = 0; i < 10; i++)
		count = count + in[i];

	*out = count;
}
