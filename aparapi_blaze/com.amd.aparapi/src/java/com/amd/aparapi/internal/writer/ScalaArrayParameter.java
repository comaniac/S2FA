package com.amd.aparapi.internal.writer;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import com.amd.aparapi.internal.model.ClassModel;

public class ScalaArrayParameter implements ScalaParameter {
	private final String type;
	private final String name;
	private final Class<?> clazz;
	private final DIRECTION dir;
	private final List<String> typeParameterDescs;
	private final List<Boolean> typeParameterIsObject;

	public ScalaArrayParameter(String fullSig, String name, DIRECTION dir) {
		this.name = name;
		this.clazz = null;
		this.dir = dir;

		this.typeParameterDescs = new LinkedList<String>();
		this.typeParameterIsObject = new LinkedList<Boolean>();

		if (fullSig.charAt(0) != '[')
			throw new RuntimeException(fullSig);

		String eleSig = fullSig.substring(1);
		if (eleSig.indexOf('<') != -1) {
			String topLevelType = eleSig.substring(0, eleSig.indexOf('<'));
			if (topLevelType.charAt(0) != 'L')
				throw new RuntimeException(fullSig);
			this.type = topLevelType.substring(1).replace('/', '.');

			String params = eleSig.substring(eleSig.indexOf('<') + 1, eleSig.lastIndexOf('>'));
			if (params.indexOf('<') != -1 || params.indexOf('>') != -1)
				throw new RuntimeException("Do not support nested parameter templates: " + fullSig);
			String[] tokens = params.split(",");
			for (int i = 0; i < tokens.length; i++) {
				String t = tokens[i];
				if (t.equals("I") || t.equals("F") || t.equals("D")) {
					this.typeParameterDescs.add(t);
					this.typeParameterIsObject.add(false);
				} else {
					this.typeParameterDescs.add("L" + t.replace('/', '.') + ";");
					this.typeParameterIsObject.add(true);
				}
			}
		} else {
			if (eleSig.equals("I"))
				this.type = "int";
			else if (eleSig.equals("D"))
				this.type = "double";
			else if (eleSig.equals("F"))
				this.type = "float";
			else if (eleSig.startsWith("L"))
				this.type = eleSig.substring(1, eleSig.length() - 1).replace('/', '.');
			else
				throw new RuntimeException("Invalid type: " + eleSig);
		}
	}

	public ScalaArrayParameter(String type, Class<?> clazz, String name,
	                           DIRECTION dir) {
		this.type = type.trim();
		this.clazz = clazz;
		this.name = name;
		this.dir = dir;
		this.typeParameterDescs = new LinkedList<String>();
		this.typeParameterIsObject = new LinkedList<Boolean>();
	}

	public void addTypeParameter(String s, boolean isObject) {
		typeParameterDescs.add(s);
		typeParameterIsObject.add(isObject);
	}

	public String[] getDescArray() {
		String[] arr = new String[typeParameterDescs.size()];
		int index = 0;
		for (String param : typeParameterDescs) {
			arr[index] = param;
			index++;
		}
		return arr;
	}

	public String getTypeParameter(int i) {
		if (i < typeParameterDescs.size())
			return typeParameterDescs.get(i);
		else
			return null;
	}

	public boolean typeParameterIsObject(int i) {
		if (i < typeParameterIsObject.size())
			return typeParameterIsObject.get(i);
		return false;
	}

	@Override
	public DIRECTION getDir() {
		return dir;
	}

	public String getName() {
		return name;
	}

	@Override
	public Class<?> getClazz() {
		return clazz;
	}

	public String getType() {
		StringBuilder sb = new StringBuilder();
		sb.append(type.replace('.', '_'));
		for (String typeParam : typeParameterDescs) {
			sb.append("_");
			if (typeParam.charAt(0) == 'L') {
				sb.append(typeParam.substring(1,
                  typeParam.length() - 1).replace(".", "_"));
			} else
				sb.append(typeParam);
		}
		// sb.append("*");
		return sb.toString();
	}

	private String getParameterStringFor(KernelWriter writer, int field) {
		final String param;
		if (!typeParameterIsObject(field)) {
			param = "__global " + ClassModel.convert(
			          typeParameterDescs.get(field), "", true) + "* " + name + "_" + (field + 1);
		} else {
			String fieldDesc = typeParameterDescs.get(field);
			if (fieldDesc.charAt(0) != 'L' ||
			    fieldDesc.charAt(fieldDesc.length() - 1) != ';')
				throw new RuntimeException("Invalid object signature \"" + fieldDesc + "\"");
			fieldDesc = fieldDesc.substring(1, fieldDesc.length() - 1);
			param = "__global " + fieldDesc.replace('.', '_') + "* " + name +
			        "_" + (field + 1);
		}
		return param;
	}

	@Override
	public String getInputParameterString(KernelWriter writer) {
		if (dir != DIRECTION.IN)
			throw new RuntimeException();

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

