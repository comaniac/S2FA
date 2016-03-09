package com.amd.aparapi.internal.writer;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import com.amd.aparapi.internal.model.ClassModel;

public class ScalaObjectParameter extends ScalaParameter {

	public ScalaObjectParameter(String fullSig, String name, DIRECTION dir) {
		super(fullSig, name, dir);
	}

	@Override
	public String getInputParameterString(KernelWriter writer) {
		if (!isReference)
			return type + " * " + name;
		else
			return type + " " + name;
	}

	@Override
	public String getOutputParameterString(KernelWriter writer) {
		if (!isReference)
			return type + " * " + name;
		else
			return type + " " + name;
	}

	@Override
	public String getAssignString(KernelWriter writer) {
		return type + " this_" + name + " = " + name;
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

