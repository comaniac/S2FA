package com.amd.aparapi.internal.writer;

public class OpenCLHostUtil
{
	public static String strErrorCheck() {
		String str = "   ";
		str = str + "if(err != CL_SUCCESS) {\n";
		str = str + "   printf(\"Error: OpenCL host\");\n";
		str = str + "   return EXIT_FAILURE;\n";
		str = str + "   }\n";

		return str;
	}

	public static String strHeadList() {
		String str = "";	
		str = str + "#include <sys/types.h>\n";
		str = str + "#include <sys/ipc.h>  \n";
		str = str + "#include <sys/shm.h>  \n";
		str = str + "#include <sys/sem.h>  \n";
		str = str + "#include <unistd.h>   \n";
		str = str + "#include <fcntl.h>    \n";
		str = str + "#include <stdio.h>    \n";
		str = str + "#include <stdlib.h>   \n";
		str = str + "#include <string.h>   \n";
		str = str + "#include <math.h>     \n";
		str = str + "#include <unistd.h>   \n";
		str = str + "#include <assert.h>   \n";
		str = str + "#include <stdbool.h>  \n";
		str = str + "#include <sys/types.h>\n";
		str = str + "#include <sys/stat.h> \n";
		str = str + "#include <CL/opencl.h>\n";

		return str;
	}

	public static String strLoadBitStreamFileFn() {
		return 	"int\n" +
						"load_file_to_memory(const char *filename, char **result)\n" +
						"{ \n" +
						"  int size = 0;\n" +
						"  FILE *f = fopen(filename, \"rb\");\n" +
						"  if (f == NULL) \n" +
						"  { \n" +
						"    *result = NULL;\n" +
						"    return -1; // -1 means file opening fail \n" +
						"  } \n" +
						"  fseek(f, 0, SEEK_END);\n" +
						"  size = ftell(f);\n" +
						"  fseek(f, 0, SEEK_SET);\n" +
						"  *result = (char *)malloc(size+1);\n" +
						"  if (size != fread(*result, sizeof(char), size, f)) \n" +
						"  { \n" +
						"    free(*result);\n" +
						"    return -2; // -2 means file reading fail \n" +
						"  } \n" +
						"  fclose(f);\n" +
						"  (*result)[size] = 0;\n" +
						"  return size;\n" +
						"}\n\n";
	}

	public static String strShmSetup() {
		String str = "";

		// Define
		str = str +	"\n" +
			"#define SHM_DONE_TAG \"shm_done\"\n" + 
			"#define INPUT 0\n" +
			"#define OUTPUT 1\n";

		// Variables and attach the shared memory
		str = str +
			"  int shmid[2];\n" +
			"  char **shm_addr;\n" +
			"\n" +
			"  shm_addr = (char **)malloc(sizeof(char *) * 2);\n" +
			"  shmid[INPUT] = atoi(argv[2]);\n" +
			"  shmid[OUTPUT] = atoi(argv[3]);\n" +
			"\n" +
			"  if(shmid[INPUT] != -1) {\n" +
			"    if((shm_addr[INPUT] = (char *) shmat(shmid[INPUT], 0, 0)) == (char *) -1) {\n" +
			"      perror(\"[OpenCL Host] shmat failed.\");\n" +
			"      exit(1);\n" +
			"    }\n" +
			"    else\n" +
			"      fprintf(stderr, \"[OpenCL Host] attach shared memory: %p\\n\", shm_addr[INPUT]);\n" +
			"  }\n" +
			"  if(shmid[OUTPUT] != -1) {\n" +
			"    if((shm_addr[OUTPUT] = (char *) shmat(shmid[OUTPUT], 0, 0)) == (char *) -1) {\n" +
			"      perror(\"[OpenCL Host] Consumer: shmat failed.\");\n" +
			"      exit(1);\n" +
			"    }\n" +
			"    else\n" +
			"      fprintf(stderr, \"[OpenCL Host] attach shared memory: %p\\n\", shm_addr[OUTPUT]);\n" +
			"	}\n\n";

		return str;
	}

	public static String strDeviceSetup() {
		String str = "\n";
				str = str +
				"  int err;                            // error code returned from api calls\n" +
				"\n" +
				"  cl_platform_id platform_id;         // platform id\n" +
				"  cl_device_id device_id;             // compute device id \n" +
				"  cl_context context;                 // compute context\n" +
				"  cl_command_queue commands;          // compute command queue\n" +
				"  cl_program program;                 // compute program\n" +
				"  cl_kernel kernel;                   // compute kernel\n" +
				"  cl_event event;										 // event\n" +
				"   \n" +
				"  char cl_platform_vendor[1001];\n" +
				"  char cl_platform_name[1001];\n" +
				"\n" +
				"  // Connect to first platform\n" +
				"  //\n" +
				"  err = clGetPlatformIDs(1, &platform_id, NULL);\n" +
				"  if (err != CL_SUCCESS)\n" +
				"  {\n" +
				"    printf(\"Error: Failed to find an OpenCL platform!\\n\");\n" +
				"    printf(\"Test failed\\n\");\n" +
				"    return EXIT_FAILURE;\n" +
				"  }\n" +
				"  err = clGetPlatformInfo(platform_id,CL_PLATFORM_VENDOR,\n" +
				"				1000,(void *)cl_platform_vendor,NULL);\n" +
				"  if (err != CL_SUCCESS)\n" +
				"  {\n" +
				"    printf(\"Error: clGetPlatformInfo(CL_PLATFORM_VENDOR) failed!\\n\");\n" +
				"    printf(\"Test failed\\n\");\n" +
				"    return EXIT_FAILURE;\n" +
				"  }\n" +
				"  printf(\"CL_PLATFORM_VENDOR %s\\n\",cl_platform_vendor);\n" +
				"  err = clGetPlatformInfo(platform_id,CL_PLATFORM_NAME,\n" +
				"				1000,(void *)cl_platform_name,NULL);\n" +
				"  if (err != CL_SUCCESS)\n" +
				"  {\n" +
				"    printf(\"Error: clGetPlatformInfo(CL_PLATFORM_NAME) failed!\\n\");\n" +
				"    printf(\"Test failed\\n\");\n" +
				"    return EXIT_FAILURE;\n" +
				"  }\n" +
				"  printf(\"CL_PLATFORM_NAME %s\\n\",cl_platform_name);\n" +
				" \n" +
				"  // Connect to a compute device\n" +
				"  //\n" +
				"  int fpga = 0;\n" +
				"#if defined (FPGA_DEVICE)\n" +
				"  fpga = 1;\n" +
				"#endif\n" +
				"  err = clGetDeviceIDs(platform_id, \n" +
				"			fpga ? CL_DEVICE_TYPE_ACCELERATOR : CL_DEVICE_TYPE_CPU,\n" +
				"			1, &device_id, NULL);\n" +
				"  if (err != CL_SUCCESS)\n" +
				"  {\n" +
				"    printf(\"Error: Failed to create a device group!\\n\");\n" +
				"    printf(\"Test failed\\n\");\n" +
				"    return EXIT_FAILURE;\n" +
				"  }\n" +
				"	else \n" +
				"	{\n" +
				"		size_t size;\n" +
				"		err = clGetDeviceInfo(device_id, CL_DEVICE_MAX_WORK_GROUP_SIZE, sizeof(size_t), &size, NULL);\n" +
				"	}\n" +
				"  \n" +
				"  // Create a compute context \n" +
				"  //\n" +
				"  context = clCreateContext(0, 1, &device_id, NULL, NULL, &err);\n" +
				"  if (!context)\n" +
				"  {\n" +
				"    printf(\"Error: Failed to create a compute context!\\n\");\n" +
				"    printf(\"Test failed\\n\");\n" +
				"    return EXIT_FAILURE;\n" +
				"  }\n" +
				"\n" +
				"  // Create a command commands\n" +
				"  //\n" +
				"  commands = clCreateCommandQueue(context, device_id, 0, &err);\n" +
				"  if (!commands)\n" +
				"  {\n" +
				"    printf(\"Error: Failed to create a command commands!\\n\");\n" +
				"    printf(\"Error: code %i\\n\",err);\n" +
				"    printf(\"Test failed\\n\");\n" +
				"    return EXIT_FAILURE;\n" +
				"  }\n" +
				"\n" +
				"  int status;\n" +
				"\n" +
				"  // Create Program Objects\n" +
				"  //\n" +
				"  \n" +
				"  // Load binary from disk\n" +
				"  unsigned char *kernelbinary;\n" +
				"  char *xclbin=argv[1];\n" +
				"  printf(\"loading %s\\n\", xclbin);\n" +
				"  int n_i = load_file_to_memory(xclbin, (char **) &kernelbinary);\n" +
				"  if (n_i < 0) {\n" +
				"    printf(\"failed to load kernel from xclbin: %s\\n\", xclbin);\n" +
				"    printf(\"Test failed\\n\");\n" +
				"    return EXIT_FAILURE;\n" +
				"  }\n" +
				"  size_t n = n_i;\n" +
				"  // Create the compute program from offline\n" +
				"  program = clCreateProgramWithBinary(context, 1, &device_id, &n,\n" +
				"							(const unsigned char **) &kernelbinary, &status, &err);\n" +
				"  if ((!program) || (err!=CL_SUCCESS)) {\n" +
				"    printf(\"Error: Failed to create compute program from binary %d!\\n\", err);\n" +
				"    printf(\"Test failed\\n\");\n" +
				"    return EXIT_FAILURE;\n" +
				"  }\n" +
				"\n" +
				"  // Build the program executable\n" +
				"  //\n" +
				"  err = clBuildProgram(program, 0, NULL, NULL, NULL, NULL);\n" +
				"  if (err != CL_SUCCESS)\n" +
				"  {\n" +
				"    size_t len;\n" +
				"    char buffer[2048];\n" +
				"\n" +
				"    printf(\"Error: Failed to build program executable!\\n\");\n" +
				"    clGetProgramBuildInfo(program, device_id, \n" +
				"				CL_PROGRAM_BUILD_LOG, sizeof(buffer), buffer, &len);\n" +
				"    printf(\"%s\\n\", buffer);\n" +
				"    printf(\"Test failed\\n\");\n" +
				"    return EXIT_FAILURE;\n" +
				"  }\n" +
				"\n" +
				"  // Create the compute kernel in the program we wish to run\n" +
				"  //\n" +
				"  kernel = clCreateKernel(program, \"run\", &err);\n" +
				"  if (!kernel || err != CL_SUCCESS)\n" +
				"  {\n" +
				"    printf(\"Error: Failed to create compute kernel!\\n\");\n" +
				"    printf(\"Test failed\\n\");\n" +
				"    return EXIT_FAILURE;\n" +
				"  }\n\n";

		return str;
	}

	public static String strValDecAndAssign(String type, String name, boolean isPointer, String val) {
		String str = "   ";
		if (isPointer) {
			str = str + "int " + name + "_length = " + val + ";\n   ";
			str = str + type + " *" + name + " = (" + type + " *) calloc(" + name + "_length, " + "sizeof(" + type + "));\n";
		}
		else
			str = str + type + " " + name + " = " + val + ";\n";
	
		return str;
	}

	public static String strValDecAndFromArg(String type, String name, boolean isPointer, int argnum) {
		String str = "   ";
		if (isPointer) {
			str = str + "int " + name + "_length = atoi(argv[" + argnum + "]);\n   ";
			str = str + type + " *" + name + " = (" + type + " *) calloc(" + name + "_length, " + "sizeof(" + type + "));\n";
		}
		else {
			str = str + type + " " + name + " = atoi(argv[" + argnum + "]);\n";
		}
		
		return str;
	}

	public static String strGetShmData(String type, String name, String addr) {
		String str = "   ";
		str = str + "memcpy(" + name + ", " + addr + ", sizeof(" + type + ") * " + name + "_length);\n";
		str = str + "   " + addr + " += sizeof(" + type + ") * " + name + "_length;\n";

		return str;
	}

	public static String strPushShmData(String type, String name, String addr) {
		String str = "   ";
		str = str + "memcpy(" + addr + ", " + name + ", sizeof(" + type + ") * " + name + "_length);\n";
		str = str + "   " + addr + " += sizeof(" + type + ") * " + name + "_length;\n";

		return str;
	}

	public static String strCreateOpenCLBuffer(String type, String name) {
		String str = "   ";
		str = str + "cl_mem " + name + "_buf = clCreateBuffer(context, CL_MEM_READ_WRITE, ";
		str = str + "sizeof(" + type + ") * " + name + "_length, NULL, &err);\n";
		str = str + strErrorCheck() + "\n";
	
		return str;	
	}

	public static String strWriteOpenCLBuffer(String type, String name) {
		String str = "   ";
		str = str + "err = clEnqueueWriteBuffer(commands, " + name + "_buf, CL_TRUE, 0, ";
		str = str + "sizeof(" + type + ") * " + name + "_length, " + name + ", 0, NULL, NULL);\n";

		return str;
	}

	public static String strResetOpenCLBuffer(String type, String name) {
		String str = "   ";
		str = str + "memset(" + name + ", 0, sizeof(" + type + ") * " + name + "_length);\n";
		str = str + "err = clEnqueueWriteBuffer(commands, " + name + "_buf, CL_TRUE, 0, ";
		str = str + "sizeof(" + type + ") * " + name + "_length, " + name + ", 0, NULL, NULL)\n;";

		return str;
	}

	public static String strSetKernelArgs(String type, String name, boolean hasBuffer, int argnum) {
		String str = "   ";
		str = str + "err  = clSetKernelArg(kernel, " + argnum + ", sizeof(";
		if (hasBuffer)
			str = str + "cl_mem), &" + name + "_buf);\n";
		else
			str = str + type + "), &" + name + ");\n";
		str = str + strErrorCheck() + "\n";
		
		return str;
	}

	public static String strLaunchKernel() {
		String str = "\n";
		str = str + 
			"      err = clEnqueueNDRangeKernel(commands, kernel, 1, NULL,\n" + 
      "          &global, NULL, 0, NULL, &event);\n" + 
			"      clWaitForEvents(1, &event);\n";
			
		return str;
	}

	public static String strReadOpenCLBuffer(String type, String name) {
		String str = "";
		str = str + "err = clEnqueueReadBuffer(commands, " + name + "_buf, CL_TRUE, 0, ";
		str = str + "sizeof(" + type + ") * " + name + "_length, " + name + ", 0, NULL, &readevent);\n";
		str = str + "   clWaitForEvents(1, &readevent);\n";
		
		return str;
	}

	public static String strReleaseDeviceMem() {
		String str = "\n";
	  str = str + 
				"   clReleaseProgram(program);\n" +
  			"   clReleaseKernel(kernel);\n" + 
  			"   clReleaseCommandQueue(commands);\n" +
  			"   clReleaseContext(context);\n";

		return str;
	}
}

