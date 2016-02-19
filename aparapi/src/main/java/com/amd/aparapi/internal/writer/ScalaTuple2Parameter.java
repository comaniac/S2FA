package com.amd.aparapi.internal.writer;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import com.amd.aparapi.internal.model.ClassModel;
import com.amd.aparapi.internal.util.*;

public class ScalaTuple2Parameter extends ScalaParameter {
	String firstParam;
	String secondParam;

	public ScalaTuple2Parameter(String fullSig, String name, DIRECTION dir) {
		super(fullSig, name, dir);
		try {
			clazz = Class.forName("scala.Tuple2");
		} catch (ClassNotFoundException c) {
			throw new RuntimeException(c);
		}
	}

	public ScalaTuple2Parameter(String type, Class<?> clazz, String name,
	                           DIRECTION dir) {
		super(type, clazz, name, dir);
	}

	public void setParameterString() {
		this.firstParam = getParameterStringFor(0);
		this.secondParam = getParameterStringFor(1);
	}

	public String getParameterString() {
		setParameterString();
		return firstParam + ", " + secondParam;
	}

	@Override
	public String getInputParameterString(KernelWriter writer) {
		if (dir != DIRECTION.IN)
			throw new RuntimeException("getInputParameterString can only be applied for input paramter.");

		return getParameterString();
	}

	@Override
	public String getOutputParameterString(KernelWriter writer) {
		if (dir != DIRECTION.OUT)
			throw new RuntimeException("getOutputParameterString can only be applied for output paramter.");

		return getParameterString();
	}

	@Override
	public String getStructString(KernelWriter writer) {
		setParameterString();
		return firstParam + "; " + secondParam + ";";
	}

	@Override
	public String getAssignString(KernelWriter writer) {
		if (dir != DIRECTION.IN)
			throw new RuntimeException("getAssignString can only be applied for input paramter.");

		String firstName = name + "_1";
		String secondName = name + "_2";

		StringBuilder sb = new StringBuilder();
		sb.append(getParameterStringFor(0, "this_" + firstName) + " = " + firstName + "; ");
		sb.append(getParameterStringFor(1, "this_" + secondName) + " = " + secondName);

		return sb.toString();
	}

	@Override 
	public String mapIdxToMethod(int idx) {
		switch (idx) {
			case 0: 
				return "_1";
			case 1:
				return "_2";
			default:
				return "";
		}
	}

	@Override
	public String toString() {
		String str = null;
		if (isArray())
			str += "[";
		str += " scala.Tuple2<" + this.typeParameterDescs.get(0) + ", ";
		str += this.typeParameterDescs.get(1) + "> " + name;
		return str;
	}
}

