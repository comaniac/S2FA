# J2FA: An Automated Java to FPGA Accelerator Framework 
### Description
J2FA is an automated Java to FPGA accelerator framework which performs offline OpenCL code generation from applications in JVM-based languages. J2FA has high programmability by enabling several Java classes to OpenCL kernels. The generated kernel is able to be synthesized to FPGA bit-stream or optimized by Merlin Compiler in advance.

### License
The J2FA framework is released under Apache 2.0 license.

### Installing Blaze
0. **Prerequisites**
		0. Blaze runtime system
    0. Apache Spark (tested with 1.5.1)
0. **Compiling**
    0. run `mvn package`

### Running VectorAdd example
0. **Prerequisites**
		0. Compile the example using Blaze runtime system.
0. **Execution**
		0. `./j2fa.sh examples/vecadd/app/target/vecadd-0.0.0.jar VecAdd`

### Contacts
For any question or discussion, please contact the author:

* Hao Yu (Cody): hyu@cs.ucla.edu
