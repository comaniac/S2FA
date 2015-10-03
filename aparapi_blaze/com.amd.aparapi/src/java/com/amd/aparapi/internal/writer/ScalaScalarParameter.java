package com.amd.aparapi.internal.writer;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import com.amd.aparapi.internal.model.ClassModel;

public class ScalaScalarParameter implements ScalaParameter {
	private final String type;
	private final String name;
	private final Class<?> clazz;
	private final List<String> typeParameterDescs;
	private final List<Boolean> typeParameterIsObject;

	public ScalaScalarParameter(String fullSig, String name) {
		this.name = name;
		this.clazz = null;

		this.typeParameterDescs = new LinkedList<String>();
		this.typeParameterIsObject = new LinkedList<Boolean>();

		String eleSig = fullSig;
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
			else if (eleSig.startsWith("scala"))
				this.type = eleSig.replace('/', '.');
			else
				throw new RuntimeException("Invalid type: " + eleSig);
		}
	}

	@Override
	public String getInputParameterString(KernelWriter writer) {
		return type + " " + name;
	}

	@Override
	public String getOutputParameterString(KernelWriter writer) {
		throw new UnsupportedOperationException();
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
	public Class<?> getClazz() {
		return null;
	}

	@Override
	public DIRECTION getDir() {
		return ScalaParameter.DIRECTION.IN;
	}
}
