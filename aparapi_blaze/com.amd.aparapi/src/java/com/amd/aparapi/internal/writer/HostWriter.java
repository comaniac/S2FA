/*
Copyright (c) 2010-2011, Advanced Micro Devices, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of conditions and the following
disclaimer. 

Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
disclaimer in the documentation and/or other materials provided with the distribution. 

Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products
derived from this software without specific prior written permission. 

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE 
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

If you use the software (in whole or in part), you shall adhere to all applicable U.S., European, and other export
laws, including but not limited to the U.S. Export Administration Regulations ("EAR"), (15 C.F.R. Sections 730 through
774), and E.U. Council Regulation (EC) No 1334/2000 of 22 June 2000.  Further, pursuant to Section 740.6 of the EAR,
you hereby certify that, except pursuant to a license granted by the United States Department of Commerce Bureau of 
Industry and Security or as otherwise permitted pursuant to a License Exception under the U.S. Export Administration 
Regulations ("EAR"), you will not (1) export, re-export or release to a national of a country in Country Groups D:1,
E:1 or E:2 any restricted technology, software, or source code you receive hereunder, or (2) export to Country Groups
D:1, E:1 or E:2 the direct product of such technology or software, if such foreign produced direct product is subject
to national security controls as identified on the Commerce Control List (currently found in Supplement 1 to Part 774
of EAR).  For the most current Country Group listings, or for additional information about the EAR or your obligations
under those regulations, please refer to the U.S. Bureau of Industry and Security's website at http://www.bis.doc.gov/. 

 */
package com.amd.aparapi.internal.writer;

import com.amd.aparapi.*;
import com.amd.aparapi.internal.exception.*;
import com.amd.aparapi.internal.instruction.*;
import com.amd.aparapi.internal.instruction.InstructionSet.*;
import com.amd.aparapi.internal.model.*;
import com.amd.aparapi.internal.model.ClassModel.AttributePool.*;
import com.amd.aparapi.internal.model.ClassModel.AttributePool.RuntimeAnnotationsEntry.*;
import com.amd.aparapi.internal.model.ClassModel.*;
import com.amd.aparapi.internal.model.HardCodedClassModel.TypeParameters;
import com.amd.aparapi.internal.model.ClassModel.ConstantPool.*;
import com.amd.aparapi.internal.model.FullMethodSignature;
import com.amd.aparapi.internal.model.FullMethodSignature.TypeSignature;

import java.util.*;

public abstract class HostWriter extends BlockWriter {

	 private class Var {
			public String type;
			public String name;
			public int length;
			public boolean isPointer;
			public String val; // Scalar: value, Array: length.
			public boolean known; // The value above is known or not.
			public boolean isInput;

			public Var() { ; }

			public Var(String x, String y) {
				this.type = x;
				this.name = y;
				this.length = 0;
				this.isPointer = false;
				this.val = "0";
				this.isInput = true;
		  }

			public Var(String x, String y, boolean b) {
				this.type = x;
				this.name = y;
				this.length = 0;
				this.isPointer = b;
				this.val = "0";
				this.known = false;
				this.isInput = true;
		  }

			public Var(String x, String y, boolean b, String val) {
				this.type = x;
				this.name = y;
				this.length = 0;
				this.isPointer = b;
				this.val = val;
				this.known = true;
				this.isInput = true;
		  }

		public boolean needShmData() {
			if (isPointer && !known && isInput && !name.contains("heap_"))
				return true;
			else
				return false;
		}
	 }

   private final String cvtBooleanToChar = "char ";
   private final String cvtBooleanArrayToCharStar = "char* ";
   private final String cvtByteToChar = "char ";
   private final String cvtByteArrayToCharStar = "char* ";
   private final String cvtCharToShort = "unsigned short ";
   private final String cvtCharArrayToShortStar = "unsigned short* ";
   private final String cvtIntArrayToIntStar = "int* ";
   private final String cvtFloatArrayToFloatStar = "float* ";
   private final String cvtDoubleArrayToDoubleStar = "double* ";
   private final String cvtLongArrayToLongStar = "long* ";
   private final String cvtShortArrayToShortStar = "short* ";

   // private static Logger logger = Logger.getLogger(Config.getLoggerName());

   private Entrypoint entryPoint = null;

   public Entrypoint getEntryPoint() {
     return entryPoint;
   }

   private boolean processingConstructor = false;
   private int countAllocs = 0;
   private String currentReturnType = null;
   private Set<MethodModel> mayFailHeapAllocation = null;

   /**
    * These three convert functions are here to perform
    * any type conversion that may be required between
    * Java and OpenCL.
    * 
    * @param _typeDesc
    *          String in the Java JNI notation, [I, etc
    * @return Suitably converted string, "char*", etc
    */
   @Override public String convertType(String _typeDesc, boolean useClassModel) {
      if (_typeDesc.equals("Z") || _typeDesc.equals("boolean")) {
         return (cvtBooleanToChar);
      } else if (_typeDesc.equals("[Z") || _typeDesc.equals("boolean[]")) {
         return (cvtBooleanArrayToCharStar);
      } else if (_typeDesc.equals("B") || _typeDesc.equals("byte")) {
         return (cvtByteToChar);
      } else if (_typeDesc.equals("[B") || _typeDesc.equals("byte[]")) {
         return (cvtByteArrayToCharStar);
      } else if (_typeDesc.equals("C") || _typeDesc.equals("char")) {
         return (cvtCharToShort);
      } else if (_typeDesc.equals("[C") || _typeDesc.equals("char[]")) {
         return (cvtCharArrayToShortStar);
      } else if (_typeDesc.equals("[I") || _typeDesc.equals("int[]")) {
         return (cvtIntArrayToIntStar);
      } else if (_typeDesc.equals("[F") || _typeDesc.equals("float[]")) {
         return (cvtFloatArrayToFloatStar);
      } else if (_typeDesc.equals("[D") || _typeDesc.equals("double[]")) {
         return (cvtDoubleArrayToDoubleStar);
      } else if (_typeDesc.equals("[J") || _typeDesc.equals("long[]")) {
         return (cvtLongArrayToLongStar);
      } else if (_typeDesc.equals("[S") || _typeDesc.equals("short[]")) {
         return (cvtShortArrayToShortStar);
      }
      // if we get this far, we haven't matched anything yet
      if (useClassModel) {
         return (ClassModel.convert(_typeDesc, "", true));
      } else {
         return _typeDesc;
      }
   }

   @Override public void writeReturn(Return ret) throws CodeGenException {
		 // Just return 0 for host.
     write("return 0;");
   }


   private String doIndent(String str) {
     StringBuilder builder = new StringBuilder();
     for (int i = 0; i < indent; i++) {
       builder.append("   ");
     }
     builder.append(str);
     return builder.toString();
   }

   @Override public String getAllocCheck() {
     assert(currentReturnType != null);
     final String nullReturn;
     if (currentReturnType.startsWith("L") || currentReturnType.startsWith("[")) {
       nullReturn = "0x0";
     } else if (currentReturnType.equals("I") || currentReturnType.equals("L") || currentReturnType.equals("F") || currentReturnType.equals("D")) {
       nullReturn = "0";
     } else {
       throw new RuntimeException("Unsupported type descriptor " + currentReturnType);
     }

     String checkStr = "if (this->alloc_failed) { return (" + nullReturn + "); }";
     String indentedCheckStr = doIndent(checkStr);

     return indentedCheckStr;
   }

   @Override public void writeConstructorCall(ConstructorCall call) throws CodeGenException {
			// do nothing
			;
   }

   private void emitExternalObjectDef(ClassModel cm) {
       final ArrayList<FieldNameInfo> fieldSet = cm.getStructMembers();

       final String mangledClassName = cm.getMangledClassName();
       newLine();
       write("typedef struct " + mangledClassName + "_s {");
       in();
       newLine();

       if (fieldSet.size() > 0) {
           int totalSize = 0;
           int alignTo = 0;

           final Iterator<FieldNameInfo> it = fieldSet.iterator();
           while (it.hasNext()) {
               final FieldNameInfo field = it.next();
               final String fType = field.desc;
               final int fSize = entryPoint.getSizeOf(fType);

               if (fSize > alignTo) {
                   alignTo = fSize;
               }
               totalSize += fSize;

               String cType = convertType(field.desc, true);
							 assert field.desc.startsWith("["): "array type " + field.desc + " in Tuple2 is not allowed";

               if (field.desc.startsWith("L")) {
								  // comaniac: Issue #1, the struct used for kernel argument cannot have pointer type.
									// Original version use pointer to access object (L), here we use scalar instead.
									// TODO: This cause execution phase needs to push scalar array into shared memory as well.
								  cType = cType.replace('.', '_');
               }
               assert cType != null : "could not find type for " + field.desc;
               writeln(cType + " " + field.name + ";");
           }

           // compute total size for OpenCL buffer
           int totalStructSize = 0;
           if ((totalSize % alignTo) == 0) {
               totalStructSize = totalSize;
           } else {
               // Pad up if necessary
               totalStructSize = ((totalSize / alignTo) + 1) * alignTo;
           }

           out();
           newLine();
       }
       write("} " + mangledClassName + ";");
       newLine();
   }

	 private void parseVars(String str, List<Var> vars, boolean isInput) {
		 str = str.replace("__global", "");
		 for (String a_var : str.split(",")) {
			 Var var = new Var();
			 if(a_var.contains("*")) {
				 var.isPointer = true;
				 a_var = a_var.replace("*", "");
			 }
			 else
				 var.isPointer = false;
			 String type_and_name[] = a_var.trim().split(" +");
			 var.type = type_and_name[0];
			 var.name = type_and_name[1];
			 var.known = false;
			 var.isInput = isInput;
			 vars.add(var);
		 }
		 return ;
	 }

   @Override public void write(Entrypoint _entryPoint,
      Collection<ScalaArrayParameter> params) throws CodeGenException {

			// Host args:
			// Arg 1  : Bit-stream path.
			// Arg 2-3: Shared memory ID for input/output.
			// Arg 4  : Size of input and output (N).
			// Arg 5- : Size of other inputs and outputs.
			int hostArgNum = 4;
			int kernelArgNum = 0;

			// The size of heap in byte (hard coded)
			int heap_size = 10 * 1024 * 1024; // Set as 1MB

			final List<Var> vars = new ArrayList<Var>();
      entryPoint = _entryPoint;

			// Partition input and output variables.
      ScalaArrayParameter outParam = null;
			for (ScalaArrayParameter p : params) {
				newLine();

				if (p.getDir() == ScalaArrayParameter.DIRECTION.OUT) {
					assert(outParam == null);
					outParam = p;
					parseVars(p.getOutputParameterString(null), vars, false);
				} else {
					parseVars(p.getInputParameterString(null), vars, true);
				}
 			}
      assert(outParam != null);

			// The length of input and output are fixed as the variable "N".
			for(final Var var : vars) {
				var.known = true;
				var.val = "N";
			}

			// Other inputs.
      for (final ClassModelField field : _entryPoint.getReferencedClassModelFields()) {
         String signature = field.getDescriptor();

         ScalaParameter param = null;
         if (signature.startsWith("[")) {
             param = new ScalaArrayParameter(signature,
                     field.getName(), ScalaParameter.DIRECTION.IN);
         } else {
             param = new ScalaScalarParameter(signature, field.getName());
         }

         // check the suffix

         boolean isPointer = signature.startsWith("[");

				 parseVars(param.getInputParameterString(null), vars, true);
				 
         // Add int field into "this" struct for supporting java arraylength op
         // named like foo__javaArrayLength
         if (isPointer && _entryPoint.getArrayFieldArrayLengthUsed().contains(field.getName())) {
            String suffix = "";
            String lenName = field.getName() + BlockWriter.arrayLengthMangleSuffix + suffix;

						vars.add(new Var("int", lenName, false));
        }
      }

			// Heap variables. Must have prefix "heap_"
			// The length of zero and fail checking are same as output.
			// Heap size is hard coded.
      if (entryPoint.requiresHeap()) {
				vars.add(new Var("unsigned int", "heap", true, Integer.toString(heap_size)));
				vars.add(new Var("unsigned", "heap_free_idx", true, "N"));
				vars.add(new Var("unsigned int", "heap_size", false, Integer.toString(heap_size)));
				vars.add(new Var("int", "heap_proc_succ", true, "N"));
				vars.add(new Var("unsigned", "heap_any_failed", true, "1"));		
      }

			// Head files and bit-stream load function.
			write(OpenCLHostUtil.strHeadList());
			newLine();
			write(OpenCLHostUtil.strLoadBitStreamFileFn());

      // Struct def.
      List<String> lexicalOrdering = _entryPoint.getLexicalOrderingOfObjectClasses();
      Set<String> emitted = new HashSet<String>();
      for (String className : lexicalOrdering) {

        for (final ClassModel cm : _entryPoint.getModelsForClassName(className)) {
            final String mangled = cm.getMangledClassName();
            if (emitted.contains(mangled)) continue;

            emitExternalObjectDef(cm);
            emitted.add(mangled);
        }
      }

			// Start of the main function.
      write("int main(int argc, char *argv[]) {");
			in();
			newLine();

			// Shared memory setup.
			write(OpenCLHostUtil.strShmSetup());
			newLine();

			// Platform setup and build kernel from bit-stream.
			write(OpenCLHostUtil.strDeviceSetup());
			newLine();

			// "N" declaration (the length of input and output).
			write(OpenCLHostUtil.strValDecAndFromArg("int", "N", false, hostArgNum));
			write(OpenCLHostUtil.strValDecAndAssign("size_t", "global", false, "N"));
			hostArgNum += 1;

			// Kernel used variables declaration and allocation.
			for (final Var var : vars) {
				if (var.known)
					write(OpenCLHostUtil.strValDecAndAssign(var.type, var.name, var.isPointer, var.val));
				else {
					write(OpenCLHostUtil.strValDecAndFromArg(var.type, var.name, var.isPointer, hostArgNum));
					hostArgNum += 1;
				}
			}
			newLine();

			// Add length variable "N" as the lastest argument.
			vars.add(new Var("int", "N", false));

			// Data fetching from shared memory.
			write("shm_addr[INPUT] += strlen(SHM_DONE_TAG);");
			newLine();
			for (final Var var : vars) {
				if (var.needShmData())
					write(OpenCLHostUtil.strGetShmData(var.type, var.name, "shm_addr[INPUT]"));
			}
			newLine();

			// Wrtie data into OpenCL buffers and set kernel args.
			for (final Var var : vars) {
				if (var.isPointer) {
					write(OpenCLHostUtil.strCreateOpenCLBuffer(var.type, var.name));
					if (var.isInput)
						write(OpenCLHostUtil.strWriteOpenCLBuffer(var.type, var.name));
				}
				write(OpenCLHostUtil.strSetKernelArgs(var.type, var.name, var.isPointer, kernelArgNum));
				kernelArgNum += 1;
			}
			newLine();

			// Launch kernel.
			write("cl_event readevent;\n");
			newLine();
			if(entryPoint.requiresHeap()) {
				write("while(1) {");
				in();
				write(OpenCLHostUtil.strLaunchKernel());
				newLine();
				write(OpenCLHostUtil.strReadOpenCLBuffer("unsigned", "heap_any_failed"));
				write("if (heap_any_failed[0] == 0) break;\n");
				newLine();
				write(OpenCLHostUtil.strResetOpenCLBuffer("unsigned", "heap_free_idx"));
				newLine();
				write(OpenCLHostUtil.strResetOpenCLBuffer("unsigned", "heap_any_failed"));
				out();
				newLine();
				write("}\n");
			}
			else
				write(OpenCLHostUtil.strLaunchKernel());
			newLine();

			// Read results from buffers.
			for (final Var var : vars) {
				if (!var.isInput) {
					write(OpenCLHostUtil.strReadOpenCLBuffer(var.type, var.name));
					newLine();
				}
			}

			// Write results into shared memory.
			write("char *shm_addr_root = shm_addr[OUTPUT];");
			newLine();
			write("shm_addr[OUTPUT] += strlen(SHM_DONE_TAG);");
			newLine();
			for (final Var var : vars) {
				if(!var.isInput)
					write(OpenCLHostUtil.strPushShmData(var.type, var.name, "shm_addr[OUTPUT]"));
			}
			newLine();
			write("memcpy(shm_addr_root, SHM_DONE_TAG, strlen(SHM_DONE_TAG));\n");
			newLine();

			// Release memory.
			write(OpenCLHostUtil.strReleaseDeviceMem());
			newLine();
			for (final Var var : vars) {
				if (var.isPointer) {
					write("free(" + var.name + ");");
					newLine();
				}
			}
			newLine();
			write("return 0;");
			out();
			newLine();
      writeln("}");
   }

   @Override public void writeThisRef() {
			// do nothing
      ;
   }

   @Override public boolean writeInstruction(Instruction _instruction) throws CodeGenException {
			// do nothing
			return false;
   }

   public static class WriterAndHost {
     public final HostWriter writer;
     public final String host;

     public WriterAndHost(HostWriter writer, String host) {
       this.writer = writer;
       this.host = host;
     }
   }

   public static WriterAndHost writeToString(Entrypoint _entrypoint,
         Collection<ScalaArrayParameter> params) throws CodeGenException, AparapiException {

      final StringBuilder openCLStringBuilder = new StringBuilder();
      final HostWriter openCLWriter = new HostWriter() {
         private int writtenSinceLastNewLine = 0;

         @Override public void writeBeforeCurrentLine(String _string) {
           openCLStringBuilder.insert(openCLStringBuilder.length() -
               writtenSinceLastNewLine, _string + "\n");
         }

         @Override public void write(String _string) {
            int lastNewLine = _string.lastIndexOf('\n');
            if (lastNewLine != -1) {
              writtenSinceLastNewLine = _string.length() - lastNewLine - 1;
            } else {
              writtenSinceLastNewLine += _string.length();
            }
            openCLStringBuilder.append(_string);
         }
      };
      try {
         openCLWriter.write(_entrypoint, params);
      } catch (final CodeGenException codeGenException) {
         throw codeGenException;
      }

      return (new WriterAndHost(openCLWriter, openCLStringBuilder.toString().replace("$", "___")));
   }
}
