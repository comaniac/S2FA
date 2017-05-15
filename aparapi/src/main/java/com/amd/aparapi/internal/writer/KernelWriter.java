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
import com.amd.aparapi.internal.util.*;
import com.amd.aparapi.internal.exception.*;
import com.amd.aparapi.internal.instruction.*;
import com.amd.aparapi.internal.instruction.InstructionSet.*;
import com.amd.aparapi.internal.model.*;
import com.amd.aparapi.internal.model.ClassModel.AttributePool.*;
import com.amd.aparapi.internal.model.ClassModel.AttributePool.RuntimeAnnotationsEntry.*;
import com.amd.aparapi.internal.model.ClassModel.*;
import com.amd.aparapi.internal.model.CustomizedClassModel.TypeParameters;
import com.amd.aparapi.internal.model.MethodModel.METHODTYPE;
import com.amd.aparapi.internal.model.ClassModel.ConstantPool.*;
import com.amd.aparapi.internal.model.FullMethodSignature;
import com.amd.aparapi.internal.model.FullMethodSignature.TypeSignature;

import java.util.*;
import java.util.logging.*;

public abstract class KernelWriter extends BlockWriter {

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

	private static Logger logger = Logger.getLogger(Config.getLoggerName());

	private boolean processingConstructor = false;

	private boolean isPassByAddrOutput = false;

	private boolean isMapPartitions = false;

	private boolean useMerlinKernel = false;

	private int countAllocs = 0;

	private String currentReturnType = null;

	public final static Set<String> scalaMapped = new HashSet<String>();
	{
		scalaMapped.add("scala/math/package$.sqrt(D)D");
		scalaMapped.add("scala/math/package$.pow(D)D");
		scalaMapped.add("scala/math/package$.exp(D)D");
		scalaMapped.add("scala/math/package$.log(D)D");
		scalaMapped.add("scala/math/package$.log(D)D");
		scalaMapped.add("scala/math/package$.abs(D)D");
		scalaMapped.add("scala/math/package$.abs(I)I");
		scalaMapped.add("scala/math/package$.abs(F)F");
		scalaMapped.add("scala/math/package$.max(DD)D");
		scalaMapped.add("scala/math/package$.min(DD)D");
	}

	public final static Set<String> SelfMapped = new HashSet<String>();
	{
		SelfMapped.add("scala/math/package$.random()D");
	}

	public final static Map<String, String[]> XilinxMethodMap = new HashMap<String, String[]>();
	{
		XilinxMethodMap.put("sqrt", new String[] {"float"});
		XilinxMethodMap.put("pow", new String[] {"float", "float"});
		XilinxMethodMap.put("exp", new String[] {"float"});
		XilinxMethodMap.put("log", new String[] {"float"});
		XilinxMethodMap.put("abs", new String[] {"int"});
		XilinxMethodMap.put("fabs", new String[] {"float"});
		XilinxMethodMap.put("fmax", new String[] {"float", "float", "float"});
		XilinxMethodMap.put("fmin", new String[] {"float", "float", "float"});
	}

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
		if (_typeDesc.equals("Z") || _typeDesc.equals("boolean"))
			return (cvtBooleanToChar);
		else if (_typeDesc.equals("[Z") || _typeDesc.equals("boolean[]"))
			return (cvtBooleanArrayToCharStar);
		else if (_typeDesc.equals("B") || _typeDesc.equals("byte"))
			return (cvtByteToChar);
		else if (_typeDesc.equals("[B") || _typeDesc.equals("byte[]"))
			return (cvtByteArrayToCharStar);
		else if (_typeDesc.equals("C") || _typeDesc.equals("char"))
			return (cvtCharToShort);
		else if (_typeDesc.equals("[C") || _typeDesc.equals("char[]"))
			return (cvtCharArrayToShortStar);
		else if (_typeDesc.equals("[I") || _typeDesc.equals("int[]"))
			return (cvtIntArrayToIntStar);
		else if (_typeDesc.equals("[F") || _typeDesc.equals("float[]"))
			return (cvtFloatArrayToFloatStar);
		else if (_typeDesc.equals("[D") || _typeDesc.equals("double[]"))
			return (cvtDoubleArrayToDoubleStar);
		else if (_typeDesc.equals("[J") || _typeDesc.equals("long[]"))
			return (cvtLongArrayToLongStar);
		else if (_typeDesc.equals("[S") || _typeDesc.equals("short[]"))
			return (cvtShortArrayToShortStar);
		else if (_typeDesc.equals("I") || _typeDesc.equals("int"))
			return "int ";
		else if (_typeDesc.equals("F") || _typeDesc.equals("float"))
			return "float ";
		else if (_typeDesc.equals("D") || _typeDesc.equals("double"))
			return "double ";
		else if (_typeDesc.equals("J") || _typeDesc.equals("long"))
			return "long ";
		else if (_typeDesc.equals("S") || _typeDesc.equals("short"))
			return "short ";

		// if we get this far, we haven't matched anything yet
		logger.fine("Converting " + _typeDesc);
		if (useClassModel)
			return (ClassModel.convert(_typeDesc, "", true));
		else
			return _typeDesc;
	}

	@Override public void writeReturn(Return ret) throws CodeGenException {
		if (isPassByAddrOutput)
			writeInstruction(ret.getFirstChild());
		else {
			write("return");
			if (processingConstructor)
				return ;
			else if (ret.getStackConsumeCount() > 0) {
				write("(");
				writeInstruction(ret.getFirstChild());
				write(")");
			}
		}
	}

	private String doIndent(String str) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < indent; i++)
			builder.append("   ");
		builder.append(str);
		return builder.toString();
	}

	@Override public void writeConstructorCall(ConstructorCall call) throws CodeGenException {
		I_INVOKESPECIAL invokeSpecial = call.getInvokeSpecial();

		MethodEntry constructorEntry = invokeSpecial.getConstantPoolMethodEntry();
		final String constructorName =
		  constructorEntry.getClassEntry().getNameUTF8Entry().getUTF8();
		Instruction parent = call.getParentExpr();

		MethodModel m = entryPoint.getCallTarget(constructorEntry, true);
		if (m == null)
			m = entryPoint.getCustomizedCallTarget(constructorName, "<init>", parent);
		if (m == null)
			throw new RuntimeException("Unable to find constructor for " + constructorName);

		boolean isReturn = false;
		String typeName = m.getOwnerClassMangledName();
		String varName = "";

		if (parent instanceof LocalVariableTableIndexAccessor)
			varName = ((LocalVariableTableIndexAccessor) parent).getLocalVariableInfo().getVariableName();
		else {
			isReturn = true;
			varName = "returnValue";
		}

		deleteCurrentLine();

		write(typeName + " *" + varName + " = new " + typeName + "(");

		boolean isFirst = true;
		for (int i = 0; i < constructorEntry.getStackConsumeCount(); i++) {
			if (!isFirst)
				write(", ");
			isFirst = false;

			Instruction argInst = invokeSpecial.getArg(i);
			
			// The method that returns objects should be pointer 
			String retType = "";
			if (argInst instanceof MethodCall) {
				retType = ((MethodCall) argInst).getConstantPoolMethodEntry()
					.getReturnType().getType();
			}
			else if (argInst instanceof I_INVOKEINTERFACE) {
				retType = ((I_INVOKEINTERFACE) argInst).getConstantPoolInterfaceMethodEntry()
					.getReturnType().getType();
			}
			else if (argInst instanceof AccessLocalVariable) {
				LocalVariableInfo localVariableInfo = ((AccessLocalVariable) argInst).getLocalVariableInfo();
				retType = localVariableInfo.getVariableDescriptor();
			}

			if ((retType.startsWith("L") && !retType.startsWith("Ljava")) || retType.equals("Ljava/lang/Object;"))
				write("*");
			writeInstruction(argInst);
		}
		write(")");

		// Rewrite the return statement
		if (isReturn) {
			write(";");
			newLine();
			write("return (" + varName);
			// writeReturn writes: ( writeMethod ), but we delete
			// the left one to construct a new object.
		}
	}

	@Override 
	public boolean writeMethod(MethodCall _methodCall, MethodEntry _methodEntry) 
		throws CodeGenException {

		// NOTE: "argc" is inaccurate for C generation because Java doesn't have to
		// pass class fields to other methods. For example:
		// public int bar() {
		//   return my_field + 10; // "my_field" doesn't have to be an argument.
		// }
		// public int foo() {
		//   return bar(); // foo() doesn't have to pass "my_field"
		// }
		// However in C code, we have to bypass the field:
		// int bar(int my_field) { ... }
		// int foo(int my_field) { return bar(my_field); }
		//
		// Solution:
		// Looking into the MethodModel.getReferencedFields of the called method.

		final int argc = _methodEntry.getStackConsumeCount();
		final String methodName =
		  _methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
		final String methodSignature =
		  _methodEntry.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8();
		final String methodClass =
		  _methodEntry.getClassEntry().getNameUTF8Entry().getUTF8();

		// A constructor method called by invokespecial
		// i.e. Obj obj = new Obj();
		if (methodName.equals("<init>")) {
			if (!_methodEntry.toString().equals("java/lang/Object.<init>()V")) { 
				// Ignore invokespecial within the constructor
				writeConstructorCall(new ConstructorCall(((Instruction)_methodCall).getMethod(),
			                     (I_INVOKESPECIAL)_methodCall, null));
			}
			return false;
		}

		// A box/unbox method called produced by Scala
		if (methodClass.equals("scala/runtime/BoxesRunTime")) {
			final Set<String> ignorableMethods = new HashSet<String>();
			ignorableMethods.add("boxToInteger");
			ignorableMethods.add("boxToFloat");
			ignorableMethods.add("boxToDouble");
			ignorableMethods.add("unboxToFloat");
			ignorableMethods.add("unboxToInt");
			ignorableMethods.add("unboxToDouble");

			if (ignorableMethods.contains(methodName)) {
				writeInstruction(_methodCall.getArg(0));
				return false;
			} else
				throw new RuntimeException("Encountered unknown boxing method " + methodName);
		}

		final boolean isSpecial = _methodCall instanceof I_INVOKESPECIAL;
		MethodModel m = entryPoint.getCallTarget(_methodEntry, isSpecial);

		// Look for customized class models
		if (m == null) {
			Instruction i = ((VirtualMethodCall) _methodCall).getInstanceReference();
			m = entryPoint.getCustomizedCallTarget(methodClass, methodName, i);
		}

		boolean noCL = _methodEntry.getOwnerClassModel().getNoCLMethods()
		               .contains(_methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8());
		if (noCL)
			return false;

		final String intrinsicMapping = Kernel.getMappedMethodName(_methodEntry);
		boolean isIntrinsic = false;

		if (intrinsicMapping == null) {
			assert entryPoint != null : "entryPoint should not be null";
			boolean isMapped = Kernel.isMappedMethod(_methodEntry);
			boolean isScalaMapped = scalaMapped.contains(_methodEntry.toString());
			boolean isSelfMapped = SelfMapped.contains(_methodEntry.toString());

			if (m == null && !_methodEntry.toString().equals("java/lang/Object.<init>()V") && 
					(isMapped || isScalaMapped || isSelfMapped))
				isIntrinsic = true;
		}

		if ((intrinsicMapping == null) && (_methodCall instanceof VirtualMethodCall) && (!isIntrinsic)) {
			Instruction i = ((VirtualMethodCall) _methodCall).getInstanceReference();
			if (i instanceof CloneInstruction)
				i = ((CloneInstruction)i).getReal();

			boolean directCall = false;

			if (i instanceof I_ALOAD_0) {
				if (m == null || (m.getMethod().getClassModel() != entryPoint.getClassModel() && 
						 !m.getMethod().getClassModel().isSuperClass(entryPoint.getClassModel().getClassWeAreModelling())))
					write("this");
				else
					directCall = true;
			} else if (i instanceof LocalVariableConstIndexLoad) {
				writeInstruction(i);
			} else if (i instanceof AccessArrayElement) {
				final AccessArrayElement arrayAccess = (AccessArrayElement)i;
				final Instruction refAccess = arrayAccess.getArrayRef();
				String varName = null;

				if (refAccess instanceof AccessField)
					varName = ((AccessField) refAccess).getConstantPoolFieldEntry().getNameAndTypeEntry()
				                         .getNameUTF8Entry().getUTF8();
				else if (refAccess instanceof AccessLocalVariable)
					varName = ((AccessLocalVariable) refAccess).getLocalVariableInfo().getVariableName();

				write(" &(" + varName);
				write("[");
				writeInstruction(arrayAccess.getArrayIndex());
				write("])");
			} else if (i instanceof New) {
				// Constructor call
				assert methodName.equals("<init>");
				writeInstruction(i);
			} else if (i instanceof AccessField) {
				String fieldName = ((AccessField) i).getConstantPoolFieldEntry()
					.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
				write(fieldName);
			} else if (i instanceof AccessLocalVariable) {
				String varName = ((AccessLocalVariable) i).getLocalVariableInfo().getVariableName();
				write(varName);
			} else
				throw new RuntimeException("unhandled call to " + _methodEntry + " from: " + i);

			if (!directCall)
				write("->");
		}

		if (intrinsicMapping == null) {
			assert entryPoint != null : "entryPoint should not be null";
			boolean isMapped = Kernel.isMappedMethod(_methodEntry);
			boolean isScalaMapped = scalaMapped.contains(_methodEntry.toString());
			boolean isSelfMapped = SelfMapped.contains(_methodEntry.toString());

			if (m != null) {
				write(m.getOwnerClassMangledName() + "_");
				if (m.getGetterField() != null)
					write("get");
				write(m.getMethodName());
			}
			else if (_methodEntry.toString().equals("java/lang/Object.<init>()V")) {
				/*
				 * Do nothing if we're in a constructor calling the
				 * java.lang.Object super constructor
				 */
			} else {
				// Must be a library call like rsqrt
				if (!isMapped && !isScalaMapped && !isSelfMapped) {
					isIntrinsic = false;
					throw new RuntimeException(_methodEntry + " should be mapped method!");
				}
				else
					isIntrinsic = true;
				write(methodName);
			}
		} else {
			write(m.getOwnerClassMangledName() + "_");
			write(intrinsicMapping);
		}

		// start writing arguments
		write("(");

		boolean isFirst = true;

		// write arguement for referenced fields
		for (final Map.Entry<String, String> entry : m.getReferencedFieldNames().entrySet()) {
			if (!isFirst)
				write(", ");
			isFirst = false;
			write(entry.getKey());
		}

		// write arguments of real method call
		for (int arg = 0; arg < argc; arg++) {
			if (!isFirst)
				write(", ");
			isFirst = false;

			// comaniac Issue #2, we have to match method arguments with Xilinx supported intrinsic functions.
			if (isIntrinsic &&
			    (_methodCall.getArg(arg) instanceof CastOperator)) {
				final CastOperator castInstruction = (CastOperator) _methodCall.getArg(arg);
				String targetType = convertCast(castInstruction.getOperator().getText());
				targetType = targetType.substring(1, targetType.length() - 1);
				String validType = getXilinxMethodArgType(methodName, arg);
				if (!targetType.equals(validType)) {
					write("(" + validType + ")");
					writeInstruction(castInstruction.getUnary());
				}
			} else {
				// The method that returns objects should be pointer 
				String retType = "";
				Instruction argInst = _methodCall.getArg(arg);
				if (argInst instanceof MethodCall) {
					retType = ((MethodCall) argInst).getConstantPoolMethodEntry()
						.getReturnType().getType();
				}
				else if (argInst instanceof I_INVOKEINTERFACE) {
					retType = ((I_INVOKEINTERFACE) argInst).getConstantPoolInterfaceMethodEntry()
						.getReturnType().getType();
				}
				if (retType.equals("Ljava/lang/Object;"))
					write("*");

				writeInstruction(argInst);
			}
		}
		write(")");
		return false; // FIXME: Previous: alloc check
	}

	@Override
	public boolean writeMethod(I_INVOKEINTERFACE _methodCall,
														 InterfaceMethodEntry _methodEntry) throws CodeGenException {
		final Instruction instanceInstruction = _methodCall.getInstanceReference();
		final String clazzName = _methodEntry.getClassEntry().getNameUTF8Entry().getUTF8();
		final String methodName = _methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();

		final int argc = _methodEntry.getStackConsumeCount();
		if (instanceInstruction instanceof I_ALOAD_0)
			write("this");
		else
			writeInstruction(instanceInstruction);

		write("->" + methodName + "(");

		boolean isFirst = true;
		for (int arg = 0; arg < argc; arg++) {
			if (!isFirst)
				write(", ");
			isFirst = false;
			writeInstruction(_methodCall.getArg(arg));
		}
		write(")");
	
		return false;
	}


	private void writeMethodDecl(MethodModel mm, List<JParameter> refParams) 
		throws CodeGenException {
		final String returnType = mm.getReturnType();
		this.currentReturnType = returnType;

		String fullReturnType;
		String convertedReturnType = convertType(returnType, true);

		if (mm.getGetterField() != null) {
			write(convertedReturnType + " ");
			write(mm.getOwnerClassMangledName() + "_");
			write("get" + mm.getMethodName() + "()");
			newLine();
			writeMethodBody(mm);
			newLine();
			return ;
		}

		// Write return type
		if (returnType.startsWith("L")) {
			SignatureEntry sigEntry =
			  mm.getMethod().getAttributePool().getSignatureEntry();
			final TypeSignature sig;
			if (sigEntry != null)
				sig = new FullMethodSignature(sigEntry.getSignature()).getReturnType();
			else
				sig = new TypeSignature(returnType);

			ClassModel cm = entryPoint.getModelFromObjectArrayFieldsClasses(
			                  convertedReturnType.trim(), new SignatureMatcher(sig));
			if (cm != null)
				fullReturnType = cm.getMangledClassName();
			else
				fullReturnType = Utils.convertToCType(entryPoint.getArgument("s2fa_out").getCType());

			// Object must be passed by address
			fullReturnType += "*";
		} else
			fullReturnType = convertedReturnType;

		// Write method name
		if (mm.getSimpleName().equals("<init>"))
			write(mm.getOwnerClassMangledName() + "(");
		else {
			write(fullReturnType + " ");
			write(mm.getOwnerClassMangledName() + "_");
			write(mm.getMethodName() + "(");
		}

		boolean isFirst = true;

		// Write reference arguments if necessary
		if ((mm.getMethod().getClassModel() == entryPoint.getClassModel()) || 
			mm.getMethod().getClassModel().isSuperClass(entryPoint.getClassModel()
			.getClassWeAreModelling())) {
				for (final Map.Entry<String, String> refField : mm.getReferencedFieldNames().entrySet()) {
					if (!isFirst)
						write(", ");
					for (JParameter param : refParams) {
						if (param.getName().equals(refField.getKey())) {
							write(param.getCType() + " " + param.getName());
							break;
						}
					}
					isFirst = false;
				}
		}

		// Write arguments
		final LocalVariableTableEntry<LocalVariableInfo> lvte = mm.getLocalVariableTableEntry();
		for (final LocalVariableInfo lvi : lvte) {
			if ((lvi.getStart() == 0) && ((lvi.getVariableIndex() != 0) ||
			                              mm.getMethod().isStatic())) { // full scope but skip this
				final String descriptor = lvi.getVariableDescriptor();

				if (!isFirst)
					write(", ");
				isFirst = false;

				final String convertedType;
				if (descriptor.startsWith("L")) {
					final String converted = convertType(descriptor, true).trim();
					final SignatureEntry sigEntry = mm.getMethod().getAttributePool().getSignatureEntry();
					final TypeSignature sig;

					if (sigEntry != null) {
						final int argumentOffset = (mm.getMethod().isStatic() ?
						                            lvi.getVariableIndex() : lvi.getVariableIndex() - 1);
						final FullMethodSignature methodSig = new FullMethodSignature(
						  sigEntry.getSignature());
						sig = methodSig.getTypeParameters().get(argumentOffset);
					} else
						sig = new TypeSignature(descriptor);
					ClassModel cm = entryPoint.getModelFromObjectArrayFieldsClasses(
					                  converted, new SignatureMatcher(sig));
					if (cm == null) { // Looking for customized class models
						JParameter param = entryPoint.getArgument(lvi.getVariableName());
						if (param == null)
							throw new RuntimeException("Cannot match argument: " + converted + 
								" " + lvi.getVariableName());
						cm = param.getClassModel();
					}
					convertedType = cm.getMangledClassName() + " *";
				} else
					convertedType = convertType(descriptor, true);
				write(convertedType);

				write(lvi.getVariableName());

				// Add item length argument for input array.
				if (descriptor.startsWith("["))
					write(", int " + lvi.getVariableName() + BlockWriter.arrayItemLengthMangleSuffix);
			}
		}

		write(")");
		newLine();
		writeMethodBody(mm);
		newLine();
		return ;
	}

	private void emitExternalObjectDef(ClassModel cm, List<MethodModel> merged, List<JParameter> refParams) 
		throws CodeGenException {
		final ArrayList<FieldNameInfo> fieldSet = cm.getStructMembers();

		final String mangledClassName = cm.getMangledClassName();
		newLine();
		write("class " + mangledClassName);
		if (cm.isDerivedClass())
			write(": public " + cm.getSuperClazz().getMangledClassName());
		write(" {");
		in();
		newLine();
		write("public:");
		newLine();

		// Write fields
		if (fieldSet.size() > 0) {
			final Iterator<FieldNameInfo> it = fieldSet.iterator();
			while (it.hasNext()) {
				final FieldNameInfo field = it.next();
				final String fType = field.desc;

				String cType = Utils.convertToCType(convertType(field.desc, true));
				assert cType != null : "could not find type for " + field.desc;
				writeln(cType + " " + field.name + ";");
// FIXME: Should be added only when necessary
//				if (cType.contains("*")) // Array field
//					writeln("int " + field.name + BlockWriter.arrayLengthMangleSuffix + ";");
			}
		}

		// Write customized derived methods (if any)
		Set<String> dispatchers = entryPoint.getKernelCalledInterfaceMethods();
		for (String dispatcher : dispatchers) {
			List<MethodModel> impls = entryPoint.getMethodImpls(dispatcher);
			logger.fine("Writing interface method " + dispatcher);

			final MethodModel sampleMM = impls.get(0);
			final String returnType = sampleMM.getReturnType();
			this.currentReturnType = returnType;

			String fullReturnType;
			String convertedReturnType = convertType(returnType, true);

//			if (sampleMM.getGetterField() != null)
//				throw new RuntimeException("Method dispatcher cannot be a getter method");

			// Write return type
			if (returnType.startsWith("L")) {
				SignatureEntry sigEntry =
				  sampleMM.getMethod().getAttributePool().getSignatureEntry();
				final TypeSignature sig;
				if (sigEntry != null)
					sig = new FullMethodSignature(sigEntry.getSignature()).getReturnType();
				else
					sig = new TypeSignature(returnType);

				ClassModel retClazzModel = entryPoint.getModelFromObjectArrayFieldsClasses(
				                  convertedReturnType.trim(), new SignatureMatcher(sig));
				if (retClazzModel != null)
					fullReturnType = retClazzModel.getMangledClassName();
				else
					fullReturnType = Utils.convertToCType(entryPoint.getArgument("s2fa_out").getCType());
			} else
				fullReturnType = convertedReturnType;

			write("virtual " + fullReturnType + " ");

			// Write method name
			String methodName = dispatcher
				.substring(dispatcher.lastIndexOf(".") + 1, dispatcher.indexOf("("));
			write(methodName + "(");

			boolean isFirst = true;

			// Write arguments
			if (sampleMM instanceof CustomizedMethodModel) {
				Map<String, String> args = ((CustomizedMethodModel<?>) sampleMM).getArgs(null);
				if (args != null) {
					for (final Map.Entry<String, String> arg : args.entrySet()) {
						if (!isFirst)
							write(", ");
						isFirst = false;

						write(arg.getValue() + " " + arg.getKey());
					}
				}
			}
			else {
				final LocalVariableTableEntry<LocalVariableInfo> lvte = sampleMM.getLocalVariableTableEntry();
				for (final LocalVariableInfo lvi : lvte) {
					if ((lvi.getStart() == 0) && ((lvi.getVariableIndex() != 0) ||
					                              sampleMM.getMethod().isStatic())) { // full scope but skip this
						final String descriptor = lvi.getVariableDescriptor();
	
						if (!isFirst)
							write(", ");
						isFirst = false;
	
						final String convertedType;
						if (descriptor.startsWith("L")) {
							final String converted = convertType(descriptor, true).trim();
							final SignatureEntry sigEntry = sampleMM.getMethod().getAttributePool().getSignatureEntry();
							final TypeSignature sig;
	
							if (sigEntry != null) {
								final int argumentOffset = (sampleMM.getMethod().isStatic() ?
								                            lvi.getVariableIndex() : lvi.getVariableIndex() - 1);
								final FullMethodSignature methodSig = new FullMethodSignature(
								  sigEntry.getSignature());
								sig = methodSig.getTypeParameters().get(argumentOffset);
							} else
								sig = new TypeSignature(descriptor);
							ClassModel retClazzModel = entryPoint.getModelFromObjectArrayFieldsClasses(
							                  converted, new SignatureMatcher(sig));
							if (retClazzModel == null) { // Looking for customized class models
								JParameter param = entryPoint.getArgument(lvi.getVariableName());
								if (param == null)
									throw new RuntimeException("Cannot match argument: " + converted + 
										" " + lvi.getVariableName());
								retClazzModel = param.getClassModel();
							}
							convertedType = retClazzModel.getMangledClassName() + " *";
						} else
							convertedType = convertType(descriptor, true);
						write(convertedType);
	
						write(lvi.getVariableName());
	
						// Add item length argument for input array.
						if (descriptor.startsWith("["))
							write(", int " + lvi.getVariableName() + BlockWriter.arrayItemLengthMangleSuffix);
					}
				}
			}
			write(") { ; }");
			newLine();
		}

		// Write methods
		for (final MethodModel mm : merged) {
			if (mm.isPrivateMemoryGetter())
				continue;

			if (mm.getOwnerClassMangledName().equals(mangledClassName)) {
				logger.fine("Writing member method " + mm.getName());
				newLine();
				writeMethodDecl(mm, refParams);
				newLine();
			}
		}

		out();
		newLine();
		write("};");
		newLine();
	}

	class SignatureMatcher extends ClassModelMatcher {
		private final TypeSignature targetSig;

		public SignatureMatcher(TypeSignature targetSig) {
			this.targetSig = targetSig;
		}

		@Override
		public boolean matches(ClassModel model) {
			String modelDesc = "L" + model.getClassWeAreModelling().getName().replace('.', '/') + ";";
			if (modelDesc.equals(targetSig.getBaseType())) {
				if (model instanceof CustomizedClassModel) {
					CustomizedClassModel hc = (CustomizedClassModel)model;

					TypeParameters hcTypes = hc.getTypeParams();
					List<String> targetTypes = targetSig.getTypeParameters();

					if (hcTypes.size() == targetTypes.size()) {
						for (int index = 0; index < hcTypes.size(); index++) {
							String target = targetTypes.get(index);
							String curr = hcTypes.get(index);
							if (!TypeSignature.compatible(target, curr))
								return false;
						}
						return true;
					} else
						return false;
				} else {
					if (!targetSig.getTypeParameters().isEmpty()) {
						throw new RuntimeException("Do not support mathing " +
						                           "loaded classes with generic types");
					}
					return true;
				}
			} else
				return false;

		}
	}

	public void writeHeader() throws CodeGenException {
		write("#include <math.h>");
		newLine();
		write("#include <string.h>");
		newLine();
	}

	@Override public void write(String kernelName, Entrypoint _entryPoint,
	                            Collection<JParameter> params) throws CodeGenException {
		String refArgsCall = "";
		String refArgsDef = "";

		entryPoint = _entryPoint;

		logger.fine("Writing the kernel");

		if (entryPoint.config.kernelType.equals("mapPartitions"))
			isMapPartitions = true;
		useMerlinKernel = entryPoint.config.enableMerlinKernel;

		// Add reference fields to argument list
		List<JParameter> refParams = new LinkedList<JParameter>();
		try {
			refParams = getRefArgs(entryPoint);
		} catch(AparapiException e) {
			logger.severe("Cannot get the reference argument list");
			System.exit(1);
		}
		for (final JParameter param : refParams) {
			if (_entryPoint.getCustomizedClassModels().hasClass(param.getTypeName()))
				_entryPoint.addCustomizedClass(param.getClassModel());

			boolean isPointer = param.isArray();

			refArgsDef += param.getParameterCode() + ", ";
			if (param instanceof ObjectJParameter && param.isArray())
				refArgsCall += "&";
			refArgsCall += param.getName() + ", ";

			// Add int field into arguements for supporting java arraylength op
			// named like foo__javaArrayLength
/*	FIXME: Should be added only when necessary
			if (isPointer && _entryPoint.getArrayFieldArrayLengthUsed().contains(field.getName())) {
				String lenName = field.getName() + BlockWriter.arrayLengthMangleSuffix;

				refArgsDef += "int " + lenName + ", ";
				refArgsCall += lenName + ", ";
			}
*/
		}

		// Remove the last ", "
		if (refArgsCall.length() > 0) {
			refArgsDef = refArgsDef.substring(0, refArgsDef.length() - 2);	
			refArgsCall = refArgsCall.substring(0, refArgsCall.length() - 2);
		}

		// Write header
		writeHeader();
		newLine();

		final List<MethodModel> merged = new ArrayList<MethodModel>(_entryPoint.getCalledMethods().size() + 1);
		merged.addAll(_entryPoint.getCalledMethods());
		merged.add(_entryPoint.getMethodModel());

		// Emit structs for oop transformation accessors
		List<String> lexicalOrdering = _entryPoint.getLexicalOrderingOfObjectClasses();
		Set<String> emitted = new HashSet<String>();
		for (String className : lexicalOrdering) {

			for (final ClassModel cm : _entryPoint.getModelsForClassName(className)) {
				final String mangled = cm.getMangledClassName();
				if (emitted.contains(mangled)) continue;

				emitExternalObjectDef(cm, merged, refParams);
				emitted.add(mangled);
			}
		}

		// Emit class declaration for modeled customized classes
		Set<String> classNameList = _entryPoint.getCustomizedClassModels().getClassList();
		for (String name : classNameList) {
			List<CustomizedClassModel> modeledClasses = _entryPoint.	
				getCustomizedClassModels().get(name);

			// Skip the first instance (sample)
			for (int i = 1; i < modeledClasses.size(); i += 1) {
				newLine();
				write(modeledClasses.get(i).getStructCode());
				newLine();
				newLine();
			}
		}

		write("// Kernel source code starts here");
		newLine();

		// Write method declaration
		for (final MethodModel mm : merged) {
			if (mm.isPrivateMemoryGetter())
				continue;
			if (lexicalOrdering.contains(mm.getOwnerClassName()))
				continue;
			logger.fine("Writing method " + mm.getName());
			writeMethodDecl(mm, refParams);
		}

		// Start writing kernel function
		JParameter outParam = null;
		newLine();
		write("void " + kernelName + "(");
		in();
		in();
		newLine();

		// Main method argunments: (dataNum, input, output, reference)
		boolean first = true;
		for (JParameter p : params) {
			if (first) {
				first = false;
				write("int N");
			}
			write(", ");
			newLine();

			// Write arguments and find output parameter
			String paramCode = null;
			if (p.getDir() == JParameter.DIRECTION.OUT) {
				assert(outParam == null); // Expect only one output parameter.
				outParam = p;
			}
			paramCode = p.getParameterCode();

			write(paramCode);

			// Add length and item number for 1-D array I/O.
			if (p.isArray()) {
				write(", ");
				newLine();
				write("int " + p.getName() + BlockWriter.arrayItemLengthMangleSuffix);
			}
		}

		// Write reference data
		if (refArgsDef.length() > 0) {
			write(", ");
			newLine();
			write(refArgsDef);
		}

		write(") {");
		out();
		newLine();
		assert(outParam != null);

		write("for (int idx = 0; idx < N; idx++) {");
		in();
		newLine();

		// Call the kernel function.
		first = true;

		write(outParam.getName() + "[idx] = ");
		if (outParam.isArray() || (outParam instanceof ObjectJParameter))
			write("*");
		write(_entryPoint.getMethodModel().getOwnerClassMangledName() + "_");
		write(_entryPoint.getMethodModel().getMethodName() + "(");
		write(refArgsCall);
		if (refArgsCall.length() > 0)
			first = false;

		for (JParameter p : params) {
			if (p.getDir() == JParameter.DIRECTION.IN) {
				if (!first)	
					write(", ");
				if (p.isArray()) { // Deserialized access
					write("&" + p.getName() + "[idx * " + p.getName() + 
							BlockWriter.arrayItemLengthMangleSuffix + "]");
					write(", " + p.getName() + BlockWriter.arrayItemLengthMangleSuffix);
				}
				else { // One-by-one access
					if (!p.isPrimitive()) // Objects are always passed by address
						write("&");
					write(p.getName() + "[idx]");
				}
				first = false;
			}
		}
		write(");");
		out();
		newLine();

		write("}");
		out();
		newLine();
		writeln("}");
	}

	@Override public boolean writeInstruction(Instruction _instruction) throws CodeGenException {
		if ((_instruction instanceof I_IUSHR) || (_instruction instanceof I_LUSHR)) {
			final BinaryOperator binaryInstruction = (BinaryOperator) _instruction;
			final Instruction parent = binaryInstruction.getParentExpr();
			boolean needsParenthesis = true;

			if (parent instanceof AssignToLocalVariable)
				needsParenthesis = false;
			else if (parent instanceof AssignToField)
				needsParenthesis = false;
			else if (parent instanceof AssignToArrayElement)
				needsParenthesis = false;
			if (needsParenthesis)
				write("(");

			if (binaryInstruction instanceof I_IUSHR)
				write("((unsigned int)");
			else
				write("((unsigned long)");
			writeInstruction(binaryInstruction.getLhs());
			write(")");
			write(" >> ");
			writeInstruction(binaryInstruction.getRhs());

			if (needsParenthesis)
				write(")");
			return false;
		} else
			return super.writeInstruction(_instruction);
	}

	public static class WriterAndKernel {
		public final KernelWriter writer;
		public final String kernel;

		public WriterAndKernel(KernelWriter writer, String kernel) {
			this.writer = writer;
			this.kernel = kernel;
		}
	}

	public static String getXilinxMethodArgType(String _method, int idx) {
		return XilinxMethodMap.get(_method)[idx];
	}

	public static String postProcforHLS(String kernel) {
		String xKernel = kernel.replace("$", "___");

		// Add specified work group number
		//xKernel = xKernel.replace("__kernel", "__kernel __attribute__((reqd_work_group_size(1, 1, 1)))");

		// Add loop pipeline to each for-loop
		//xKernel = xKernel.replace("for (", "__attribute__((xcl_pipeline_loop)) for (");

		// Add loop pipeline to each while-loop
		//xKernel = xKernel.replace("while (", "__attribute__((xcl_pipeline_loop)) while (");

		return xKernel;
	}

	public static WriterAndKernel writeToString(String kernelName, Entrypoint _entrypoint,
	    Collection<JParameter> params) throws CodeGenException, AparapiException {

		final StringBuilder openCLStringBuilder = new StringBuilder();
		final KernelWriter openCLWriter = new KernelWriter() {
			private int writtenSinceLastNewLine = 0;

			@Override public void deleteCurrentLine() {
				openCLStringBuilder.delete(openCLStringBuilder.length() - writtenSinceLastNewLine - 1,
				                           openCLStringBuilder.length());
				newLine();
			}

			@Override public void writeBeforeCurrentLine(String _string) {
				openCLStringBuilder.insert(openCLStringBuilder.length() -
				                           writtenSinceLastNewLine, _string + "\n");
			}

			@Override public void write(String _string) {
				int lastNewLine = _string.lastIndexOf('\n');
				if (lastNewLine != -1)
					writtenSinceLastNewLine = _string.length() - lastNewLine - 1;
				else
					writtenSinceLastNewLine += _string.length();
				openCLStringBuilder.append(_string);
			}
		};
		try {
			openCLWriter.write(kernelName, _entrypoint, params);
		} catch (final CodeGenException codeGenException) {
			throw codeGenException;
		}/* catch (final Throwable t) {
         throw new CodeGenException(t);
       }*/

		String kernel = openCLStringBuilder.toString();

		return (new WriterAndKernel(openCLWriter, kernel));
	}

	public static List<JParameter> getRefArgs(Entrypoint _entrypoint) 
		throws AparapiException {
		List<JParameter> refParams = new LinkedList<JParameter>();

		for (final ClassModelField field : _entrypoint.getReferencedClassModelFields()) {
			String signature = field.getDescriptorUTF8Entry().getUTF8();
			if (signature.startsWith("L") && field.hasTypeHint())
				signature += "<" + field.getDescriptor() + ">";
			JParameter param = JParameter.createParameter(signature, field.getName(), JParameter.DIRECTION.IN);
			param.setAsReference();
			param.init(_entrypoint);
			refParams.add(param);
		}
		return refParams;
	}
}
