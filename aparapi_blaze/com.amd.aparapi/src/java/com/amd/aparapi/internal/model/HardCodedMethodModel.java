package com.amd.aparapi.internal.model;

import com.amd.aparapi.internal.writer.KernelWriter;

public abstract class HardCodedMethodModel extends MethodModel {
	public static enum METHODTYPE {
		UNKNOWN,
		VAR_ACCESS,
		STATUS_CHECK,
		CONSTRUCTOR
	}

	protected final String name;
	protected METHODTYPE methodType;

	public HardCodedMethodModel(String name, METHODTYPE methodType) {
		this.name = name;
		this.methodIsGetter = false; // TODO: For customized classes
		this.methodType = methodType;
	}

	public String getMethodName() {
		return name;
	}

	public METHODTYPE getMethodType() {
		return methodType;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override 
	public String getOwnerClassMangledName() {
		return null;
	}

	@Override
	public String getDescriptor() {
		return null;
	}

	@Override
	public String getGetterField() {
		return null;
	}

	/*
	 *	Generate method access string
	 */
	public abstract String getAccessString(String varName);

	/*
	 * Generate method declaration string
	 */
	public abstract String getDeclareString(String varName);
}
