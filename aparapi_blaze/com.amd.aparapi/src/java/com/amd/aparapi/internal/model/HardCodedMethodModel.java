package com.amd.aparapi.internal.model;

import com.amd.aparapi.internal.writer.KernelWriter;

public class HardCodedMethodModel extends MethodModel {
	private final String name;
	private final String sig;
	private final MethodDefGenerator methodDef;
	private final String getterFieldName;
	private String ownerMangledName;

	public HardCodedMethodModel(String name, String sig,
	                            MethodDefGenerator methodDef, boolean isGetter, String getterFieldName) {
		this.name = name;
		this.sig = sig;
		this.methodDef = methodDef;
		this.methodIsGetter = isGetter;
		this.getterFieldName = getterFieldName;
	}

	public void setOwnerMangledName(String s) {
		this.ownerMangledName = s;
	}

	public String getOriginalName() {
		return name;
	}

	@Override
	public String getName() {
		return getOwnerClassMangledName();
	}

	@Override
	public String getDescriptor() {
		return sig;
	}

	public String getMethodDef(HardCodedClassModel classModel, KernelWriter writer) {
		return methodDef.getMethodDef(this, classModel, writer).toString();
	}

	@Override
	public String getGetterField() {
		if (methodIsGetter)
			return getterFieldName;
		else
			return null;
	}

	@Override
	public String getOwnerClassMangledName() {
		return ownerMangledName;
	}

	public abstract static class MethodDefGenerator<T extends HardCodedClassModel> {
		public abstract String getMethodDef(HardCodedMethodModel method,
		                                    T classModel, KernelWriter writer);
	}
}
