package com.amd.aparapi.internal.writer;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.logging.*;
import com.amd.aparapi.Config;
import com.amd.aparapi.internal.model.ClassModel;
import com.amd.aparapi.internal.model.HardCodedClassModel;
import com.amd.aparapi.internal.util.Utils;

public abstract class ScalaParameter {
	public static enum DIRECTION {
		IN, OUT
	}

	protected static Logger logger = Logger.getLogger(Config.getLoggerName());

	protected HardCodedClassModel clazzModel;
	protected Class<?> clazz;
	protected boolean isReference;
	protected String type;
	protected String fullSig;
	protected final boolean arrayOrNot;
	protected boolean primitiveOrNot;
	protected boolean customizedOrNot;
	protected final String name;
	protected final DIRECTION dir;
	protected final List<ScalaParameter> typeParameters;

	public ScalaParameter(String fullSig, String name, DIRECTION dir) {
		this.name = name;
		this.clazz = null;
		this.clazzModel = null;
		this.dir = dir;
		this.isReference = false;
		this.fullSig = fullSig;
		this.customizedOrNot = false;
		this.primitiveOrNot = true;
		this.typeParameters = new LinkedList<ScalaParameter>();

		// Check type: Array
		boolean isArrayBased = false;
		if (fullSig.indexOf('<') != -1)
			isArrayBased = Utils.isArrayBasedClass(fullSig.substring(0, fullSig.indexOf("<")));

		String eleSig = null;

		if (fullSig.charAt(0) != '[' && !isArrayBased) {
			eleSig = fullSig;
			arrayOrNot = false;
		}
		else {
			eleSig = fullSig.replace("[", "");
			arrayOrNot = true;
		}

		// Check type: Customized
		if (!Utils.isPrimitive(eleSig)) {
			primitiveOrNot = false;
			String tmpSig = eleSig;
			if (tmpSig.startsWith("L"))
				tmpSig = tmpSig.substring(1);
			if (tmpSig.indexOf('<') != -1)
				tmpSig = tmpSig.substring(0, tmpSig.indexOf('<'));
			if (!Utils.isHardCodedClass(tmpSig))
				customizedOrNot = true;
		}

		if (eleSig.indexOf('<') != -1) { // Has generic types
			primitiveOrNot = false;
			String topLevelType = eleSig.substring(0, eleSig.indexOf('<'));

			// Set base class
			if (topLevelType.charAt(0) == 'L')
				this.type = topLevelType.substring(1).replace('/', '.');
			else
				this.type = topLevelType.replace('/', '.');
			logger.finest("Parameter: " + eleSig + " extracts base " + this.type);

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
					ScalaParameter newType = createScalaParameter(params.substring(curPos, i), null, dir);
					this.typeParameters.add(newType);
					curPos = i + 1;
				}
			}
			logger.finest("Add a new generic type " + params.substring(curPos));
			ScalaParameter newType = createScalaParameter(params.substring(curPos), null, dir);
			this.typeParameters.add(newType);
		} else {
			if (eleSig.equals("I"))
				this.type = "int";
			else if (eleSig.equals("D"))
				this.type = "double";
			else if (eleSig.equals("F"))
				this.type = "float";
			else {
				if (eleSig.startsWith("L"))
					this.type = eleSig.substring(1, eleSig.length() - 1).replace('/', '.');
				else
					this.type = eleSig.replace('/', '.');
			}
		}
	}

	public ScalaParameter(String type, Class<?> clazz, String name, DIRECTION dir) {
		this.type = type.trim();
		this.clazz = clazz;
		this.clazzModel = null;
		this.name = name;
		this.dir = dir;
		this.isReference = false;
		this.primitiveOrNot = false;
		this.customizedOrNot = false;
		this.typeParameters = new LinkedList<ScalaParameter>();
		if (type.charAt(0) == '[') {
			arrayOrNot = true;
			type = type.substring(1);
		}
		else
			arrayOrNot = false;
		if (type.charAt(0) == 'L')
			this.customizedOrNot = true;
	}

	public ScalaParameter(String fullSig, String name) {
		this(fullSig, name, DIRECTION.IN);
	}

	public String[] getDescArray() {
		String[] arr = new String[typeParameters.size()];
		int index = 0;
		for (ScalaParameter param : typeParameters) {
			arr[index] = param.getType();
			index++;
		}
		return arr;
	}

	public String getClassName() {
		return type;
	}

	public String getFullType() {
		return fullSig;
	}

	public String getType() {
		StringBuilder sb = new StringBuilder();

		// FIXME: Should be removed in the future
		if (type.equals("int"))
			sb.append("I");
		else if (type.equals("float"))
			sb.append("F");
		else if (type.equals("double"))
			sb.append("D");
		else if (type.equals("long"))
			sb.append("J");
		else if (type.equals("short"))
			sb.append("S");
		else
			sb.append(type.replace('.', '_'));

		for (ScalaParameter param : typeParameters) {
			sb.append("_");
			sb.append(param.getType());
		}
		return sb.toString();
	}

	protected String getParameterStringFor(int field, String name) {
		String param;
		if (!typeParameters.get(field).isCustomized()) {
			param = "__global " + ClassModel.convert(
			          typeParameters.get(field).getType(), "", true);
			if (!isReference) // Arguments must be array type
				param = param + "* ";
			param = param + name + mapIdxToMethod(field);
		} else {
			String fieldDesc = typeParameters.get(field).getType();
			if (fieldDesc.charAt(0) != 'L' ||
			    fieldDesc.charAt(fieldDesc.length() - 1) != ';')
				throw new RuntimeException("Invalid object signature \"" + fieldDesc + "\"");
			fieldDesc = fieldDesc.substring(1, fieldDesc.length() - 1);
			param = "__global " + fieldDesc.replace('.', '_') + "* " + name + (field + 1);
		}
		return param;
	}

	protected String getParameterStringFor(int field) {
		return getParameterStringFor(field, name);
	}

	public boolean isPrimitive() {
		return this.primitiveOrNot;
	}

	public void setAsReference() {
		this.isReference = true;
	}

	public String getName() {
		return name;
	}

	public Class<?> getClazz() {
		return clazz;
	}

	public HardCodedClassModel getClazzModel() {
		return clazzModel;
	}

	public DIRECTION getDir() {
		return dir; 
	}

	public boolean isArray() {
		return arrayOrNot;
	}

	public boolean isCustomized() {
		return customizedOrNot;
	}

	public static ScalaParameter createScalaParameter(String signature, String name, DIRECTION dir) {
		ScalaParameter param = null;

		if (signature.contains("scala/Tuple2"))
			param = new ScalaTuple2Parameter(signature, name, dir);
		else if (signature.contains("scala/collection/Iterator"))
			param = new ScalaIteratorParameter(signature, name, dir);
		else if (Utils.isPrimitive(signature))
			param = new ScalaScalarParameter(signature, name, dir);	
		else if (signature.startsWith("[") && Utils.isPrimitive(signature.substring(1)))
			param = new ScalaArrayParameter(signature, name, dir);
		else
			param = new ScalaObjectParameter(signature, name, dir);

		return param;
	}

	/*
	 * Map method name to index
	 */
	public abstract String mapIdxToMethod(int idx);

	/*
	 * Generate the string for method argument.
	 */
	public abstract String getInputParameterString(KernelWriter writer);

	/*
	 * Generate the string for method argument.
	 */
	public abstract String getOutputParameterString(KernelWriter writer);

	/*
	 * Generate the string for the variables in "this" struct.
	 */ 
	public abstract String getStructString(KernelWriter writer);

	/*
	 * Generate the string for assigning input variables to "this" struct.
	 */
	public abstract String getAssignString(KernelWriter writer);
}

