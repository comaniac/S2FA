package com.amd.aparapi.internal.model;

import java.util.*;
import com.amd.aparapi.internal.util.Utils;
import com.amd.aparapi.internal.writer.KernelWriter;

public abstract class CustomizedMethodModel<T extends CustomizedClassModel> 
		extends MethodModel {

	protected final T clazzModel;
	protected final String name;
	protected	final String returnType;
	protected final Map<String, String> args;
	protected final String body;
	protected CustomizedFieldModel getterField;
	protected String sig;

	public CustomizedMethodModel(T clazzModel, String name, METHODTYPE methodType) {
		this.clazzModel = clazzModel;
		this.name = name;
		this.methodType = methodType;
		this.getterField = null;

		this.returnType = getReturnType(clazzModel);
		this.args = getArgs(clazzModel);
		this.body = getBody(clazzModel);
		this.sig = this.name + "(" + clazzModel.getClassName();
		if (this.args != null) {
			for (Map.Entry<String, String> arg : this.args.entrySet())
				this.sig += ", " + arg.getValue() + " " + arg.getKey();
		}
		this.sig += ")" + this.returnType;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override 
	public String getOwnerClassMangledName() {
		return clazzModel.getMangledClassName();
	}

	@Override
	public String getDescriptor() {
		return sig;
	}

	@Override
	public String getGetterField() {
		if (getterField != null)
			return getterField.getName();
		return null;
	}

	public void setGetterField(CustomizedFieldModel field) {
		getterField = field;
	}

	public String getDeclareCode() {
		StringBuilder sb = new StringBuilder();
		String returnType = getReturnType(clazzModel);
		String convertedReturnType = Utils.convertToCType(returnType);
		if (!Utils.isPrimitive(returnType) && !convertedReturnType.contains("*"))
			sb.append(convertedReturnType + "* ");
		else
			sb.append(convertedReturnType + " ");
		sb.append(clazzModel.getMangledClassName() + "_");
		if (this.name.equals("<init>"))
			sb.append("_init_");
		else
			sb.append(Utils.convertToCType(this.name));
		sb.append("(" + clazzModel.getMangledClassName() + " *this");
		if (this.args != null) {
			for (Map.Entry<String, String> arg : this.args.entrySet())
				sb.append(", " + Utils.convertToCType(arg.getValue()) + " " + arg.getKey());
		}
		sb.append(") {\n");
		sb.append("  " + getBody(clazzModel) + "\n");
		sb.append("}\n");
		return sb.toString();
	}

	public abstract String getReturnType(T clazzModel);

	public abstract Map<String, String> getArgs(T clazzModel);

	public abstract String getBody(T clazzModel);

}

