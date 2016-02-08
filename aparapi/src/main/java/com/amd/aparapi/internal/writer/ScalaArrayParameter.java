package com.amd.aparapi.internal.writer;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import com.amd.aparapi.internal.model.ClassModel;

public class ScalaArrayParameter extends ScalaParameter {

	public ScalaArrayParameter(String fullSig, String name, DIRECTION dir) {
		super(fullSig, name, dir);
	}

	public ScalaArrayParameter(String type, Class<?> clazz, String name,
	                           DIRECTION dir) {
		super(type, clazz, name, dir);
	}

	@Override
	public String getInputParameterString(KernelWriter writer) {
		if (dir != DIRECTION.IN)
			throw new RuntimeException("getInputParameterString can only be applied for input paramter.");

		return "__global " + type.replace('.', '_') + "* " + name;
	}

	@Override
	public String getOutputParameterString(KernelWriter writer) {
		if (dir != DIRECTION.OUT)
			throw new RuntimeException("getOutputParameterString can only be applied for output paramter.");

		return "__global " + type.replace('.', '_') + "* " + name;
	}

	@Override
	public String getStructString(KernelWriter writer) {
		return "__global " + type.replace('.', '_') + "* " + name;
	}

	@Override
	public String getAssignString(KernelWriter writer) {
		if (dir != DIRECTION.IN)
			throw new RuntimeException("getAssignString can only be applied for input paramter.");

		return "this->" + name + " = " + name;
	}

	@Override
	public String toString() {
		return "[" + type + " " + name + ", clazz=" + clazz + "]";
	}

	@Override
	public String mapIdxToMethod(int idx) {
		return "";
	}
}

