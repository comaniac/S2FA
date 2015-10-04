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

		if (type.equals("scala.Tuple2")) {
			final String firstParam = getParameterStringFor(writer, 0);
			final String secondParam = getParameterStringFor(writer, 1);
// #Issue 49: We don't use container anymore
//				String containerParam = "__global " + getType() + " *" + name;
			return firstParam + ", " + secondParam;
		} else
			return "__global " + type.replace('.', '_') + "* " + name;
	}

	@Override
	public String getOutputParameterString(KernelWriter writer) {
		if (dir != DIRECTION.OUT)
			throw new RuntimeException();

		if (type.equals("scala.Tuple2")) {
			final String firstParam = getParameterStringFor(writer, 0);
			final String secondParam = getParameterStringFor(writer, 1);
			return firstParam + ", " + secondParam;
		} else
			return "__global " + type.replace('.', '_') + "* " + name;
	}

	@Override
	public String getStructString(KernelWriter writer) {
		if (type.equals("scala.Tuple2")) {
			if (dir == DIRECTION.OUT) {
				return getParameterStringFor(writer, 0) + "; " +
				       getParameterStringFor(writer, 1) + "; ";
			} else
				return "__global " + getType() + " *" + name;
		} else
			return "__global " + type.replace('.', '_') + "* " + name;
	}

	@Override
	public String getAssignString(KernelWriter writer) {
		if (dir != DIRECTION.IN)
			throw new RuntimeException();

		if (type.equals("scala.Tuple2")) {
			StringBuilder sb = new StringBuilder();
			sb.append("this->" + name + " = " + name + "; ");
			sb.append("for (int i = 0; i < " + name + "__javaArrayLength; i++) { ");

			// comaniac: Issue #1, we use scalar instead of pointer for kernel argument structure type.
			// It means that we cannot use pointer assignment.
			// Restriction: Tuple2 doesn't allow Array type.
			// TODO: Recognize the platform and generate different kernels.
			sb.append(name + "[i]._1 = " + name + "_1[i]; ");
			sb.append(name + "[i]._2 = " + name + "_2[i]; ");
			sb.append(" } ");
			return sb.toString();
		} else
			return "this->" + name + " = " + name;
	}

	@Override
	public String toString() {
		return "[" + type + " " + name + ", clazz=" + clazz + "]";
	}
}

