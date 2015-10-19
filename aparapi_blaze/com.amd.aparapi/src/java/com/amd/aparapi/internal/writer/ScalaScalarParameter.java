package com.amd.aparapi.internal.writer;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import com.amd.aparapi.internal.model.ClassModel;

public class ScalaScalarParameter extends ScalaParameter {

	public ScalaScalarParameter(String fullSig, String name, DIRECTION dir) {
		super(fullSig, name, dir);
	}

	@Override
	public String getInputParameterString(KernelWriter writer) {
		return type + " " + name;
	}

	@Override
	public String getOutputParameterString(KernelWriter writer) {
		// Output argument must be pointer type
		return "__global " + type + " * " + name;
	}

	@Override
	public String getAssignString(KernelWriter writer) {
		return "this->" + name + " = " + name;
	}

	@Override
	public String getStructString(KernelWriter writer) {
		return type + " " + name;
	}

	@Override
	public String mapIdxToMethod(int idx) {
		return "";
	}
}
