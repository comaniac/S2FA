package com.amd.aparapi.internal.writer;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.logging.*;
import com.amd.aparapi.Config;
import com.amd.aparapi.internal.model.Entrypoint;
import com.amd.aparapi.internal.model.ClassModel;
import com.amd.aparapi.internal.model.CustomizedClassModel;
import com.amd.aparapi.internal.util.Utils;

public abstract class JParameter {
	public static enum DIRECTION {
		IN, OUT
	}

	protected static Logger logger = Logger.getLogger(Config.getLoggerName());

	protected CustomizedClassModel clazzModel;
	protected boolean referenceOrNot;
	protected boolean arrayOrNot;
	protected String type;
	protected final String name;
	protected final DIRECTION dir;
	protected final List<JParameter> typeParameters;

	public JParameter(String fullSig, String name, DIRECTION dir) {
		this.name = name;
		this.clazzModel = null;
		this.dir = dir;
		this.referenceOrNot = false;
		this.typeParameters = new LinkedList<JParameter>();

		String eleSig = fullSig;
		if (eleSig.startsWith("[")) {
			eleSig = eleSig.substring(1);
			arrayOrNot = true;
		}
		else
			arrayOrNot = false;
		String tmpType = eleSig;

		if (eleSig.indexOf('<') != -1) { // Has generic types
			String topLevelType = eleSig.substring(0, eleSig.indexOf('<'));

			// Set base class
			tmpType = topLevelType;
			logger.finest("JParameter: " + eleSig + " extracts base " + topLevelType);

			// Extract generic types
			String params = eleSig.substring(eleSig.indexOf('<') + 1, eleSig.lastIndexOf('>'));
			int curPos = 0;
			int nestLevel = 0;
			for (int i = 0; i < params.length(); i++) {
				if (params.charAt(i) == '<')
					nestLevel += 1;
				else if (params.charAt(i) == '>')
					nestLevel -= 1;
				else if (params.charAt(i) == ',' && nestLevel == 0) {
					logger.finest("Add a new generic type " + params.substring(curPos, i));
					JParameter newType = createParameter(params.substring(curPos, i), null, dir);
					this.typeParameters.add(newType);
					curPos = i + 1;
				}
			}
			logger.finest("Add a new generic type " + params.substring(curPos));
			JParameter newType = createParameter(params.substring(curPos), null, dir);
			this.typeParameters.add(newType);
		}
		this.type = tmpType.replace("/", ".");
	}

	public List<JParameter> getTypeParameters() {
		return typeParameters;
	}

	public String[] getDescArray() {
		String[] arr = new String[typeParameters.size()];
		int index = 0;
		for (JParameter param : typeParameters) {
			if (param.isArray())
				arr[index] = "[";
			else
				arr[index] = "";
			arr[index] += param.getTypeName();
			index++;
		}
		return arr;
	}

	public String getTypeName() {
		if (type.startsWith("L"))
			return type.substring(1).replace(";", "");
		return type;
	}

	public String getFullType() {
		StringBuilder sb = new StringBuilder();

		sb.append(type.replace('.', '_'));

		for (JParameter param : typeParameters) {
			sb.append("_");
			sb.append(param.getFullType());
		}

		return sb.toString();
	}

	public String getCType() {
		String s = "";
		
		s += Utils.convertToCType(type);
		s = s.replace("*", "");

		for (JParameter param : typeParameters)
			s += "_" + param.getCType();
		s = s.replace("*", "Ary");

		if (isArray())
			s += "*";

		return s;
	}

	public boolean isArray() {
		return arrayOrNot;
	}

	public boolean isReference() {
		return referenceOrNot;
	}

	public void setAsReference() {
		this.referenceOrNot = true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();

		if (isArray())
			sb.append("[");
		sb.append(getTypeName());
		boolean first = true;

		for (JParameter param : typeParameters) {
			if (first == true)
				sb.append("<");
			else
				sb.append(",");
			sb.append(param.toString());
			first = false;
		}
		if (first == false)
			sb.append(">");

		return sb.toString();
	}

	public String getName() {
		return name;
	}

	public CustomizedClassModel getClassModel() {
		return clazzModel;
	}

	public DIRECTION getDir() {
		return dir; 
	}

	public static JParameter createParameter(String signature, String name, DIRECTION dir) {
		JParameter param = null;

		if (Utils.isPrimitive(signature))
			param = new PrimitiveJParameter(signature, name, dir);	
		else
			param = new ObjectJParameter(signature, name, dir);

		return param;
	}

	/*
	 * Indicate if this parameter is a primitive type.
	 */ 
	public abstract boolean isPrimitive();

	/*
	 * Initialize the parameter in Entrypoint such as make a new instance.
	 */
	public abstract void init(Entrypoint ep);

	/*
	 * Generate the string for method arguments.
	 */
	public abstract String getParameterCode();
}

