package com.amd.aparapi.internal.model;

import java.util.*;
import com.amd.aparapi.internal.writer.KernelWriter;

public abstract class CustomizedMethodModel<T extends CustomizedClassModel> 
		extends MethodModel {

	protected final T clazzModel;
	protected final String name;
	protected	final String returnType;
	protected final ArrayList<String> args;
	protected final String body;
	protected String sig;

	public CustomizedMethodModel(T clazzModel, String name, METHODTYPE methodType) {
		this.clazzModel = clazzModel;
		this.name = name;
		this.methodType = methodType;

		this.returnType = getReturnType(clazzModel);
		this.args = getArgs(clazzModel);
		this.body = getBody(clazzModel);
		this.sig = this.name + "(" + clazzModel.getClassName();
		if (this.args != null) {
			for (String arg : this.args)
				this.sig += ", " + arg;
		}
		this.sig += ")" + this.returnType;
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
		return sig;
	}

	@Override
	public String getGetterField() {
		return null;
	}

	public String getDeclareCode(String varName) {
		StringBuilder sb = new StringBuilder();
		String returnType = getReturnType(clazzModel);
		if (returnType.contains("[]"))
			returnType = returnType.replace("[]", "").trim() + " *";
		sb.append(returnType + " " + this.name + "(");
		sb.append(clazzModel.getClassName() + " *this");
		for (String arg : getArgs(clazzModel))
			sb.append(", " + arg);
		sb.append("( {\n");
		sb.append(getBody(clazzModel));
		sb.append("}\n");
		return sb.toString();
	}

	public abstract String getReturnType(T clazzModel);

	public abstract ArrayList<String> getArgs(T clazzModel);

	public abstract String getBody(T clazzModel);
}

