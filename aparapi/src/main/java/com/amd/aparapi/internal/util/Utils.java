package com.amd.aparapi.internal.util;

import java.util.*;
import com.amd.aparapi.internal.model.MethodModel.METHODTYPE;
import com.amd.aparapi.internal.model.*;
import com.amd.aparapi.internal.writer.*;
import com.amd.aparapi.internal.writer.JParameter.DIRECTION;

/**
 * This utility class encapsulates the necessary actions required when processing and generating the kernel.
 */
public class Utils {

	public static String cleanClassName(String clazz) {
		String tname = clazz.replace(".", "/").replace(";", "").trim();
		if (tname.contains("$")) // Get rid of inner class names
			tname = tname.substring(0, tname.indexOf("$"));
		if (tname.startsWith("L"))
			tname = tname.substring(1);
		return tname;
	}

	public static boolean isPrimitive(String _type) {
		String type = _type.replace("[", "").trim();

		if (type.startsWith("I") || type.startsWith("F") || 
				type.startsWith("D") || type.startsWith("J") || 
				type.startsWith("B") || type.startsWith("C") ||
				type.startsWith("S") || type.startsWith("Z"))
			return true;
		else
			return false;
	}

	public static String convertToCType(String type) {
		boolean isArray = false;
		String newType = "";

		if (type.startsWith("[")) {
			isArray = true;
			type = type.substring(1);
		}

		if (type.equals("I") || type.equals("Z"))
			newType += "int";
		else if (type.equals("F"))
			newType += "float";
		else if (type.equals("D"))
			newType += "double";
		else if (type.equals("J"))
			newType += "long";
		else if (type.equals("S"))
			newType += "short";
		else if (type.equals("C") || type.equals("B"))
			newType += "char";
		else {
			if (type.startsWith("L"))
				newType += type.substring(1);
			else
				newType += type;
			newType = newType.replace(".", "_")
				.replace("/", "_").replace(";", "");
		}

		if (isArray)
			newType += "*";
		return newType;
	}

	public static String convertToBytecodeType(String type) {
		String newType = "";

		if (type.contains("[]")) {
			type = type.replace("[]", "").trim();
			newType = newType + "[";
		}

		if (type.equals("int"))
			newType += "I";
		else if (type.equals("float"))
			newType += "F";
		else if (type.equals("double"))
			newType += "D";
		else if (type.equals("long"))
			newType += "J";
		else if (type.equals("short"))
			newType += "S";
		else if (type.equals("char"))
			newType += "C";
		else if (type.equals("boolean"))
			newType += "Z";
		else if (type.equals("byte"))
			newType += "B";
		else
			newType += type;
		return newType;
	}
}
