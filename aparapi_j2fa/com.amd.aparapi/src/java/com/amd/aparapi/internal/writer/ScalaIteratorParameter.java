package com.amd.aparapi.internal.writer;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import com.amd.aparapi.internal.model.ClassModel;

public class ScalaIteratorParameter extends ScalaParameter {

	public ScalaIteratorParameter(String fullSig, String name, DIRECTION dir) {
		super(fullSig, name, dir);
	}

	public ScalaIteratorParameter(String type, Class<?> clazz, String name,
	                           DIRECTION dir) {
		super(type, clazz, name, dir);
	}

	@Override
	public String getInputParameterString(KernelWriter writer) {
		if (dir != DIRECTION.IN)
			throw new RuntimeException("getInputParameterString can only be applied for input paramter.");

		return getParameterStringFor(0);
	}

	@Override
	public String getOutputParameterString(KernelWriter writer) {
		if (dir != DIRECTION.OUT)
			throw new RuntimeException("getOutputParameterString can only be applied for output paramter.");

		return getParameterStringFor(0);
	}

	@Override
	public String getStructString(KernelWriter writer) {
		return getParameterStringFor(0);
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

