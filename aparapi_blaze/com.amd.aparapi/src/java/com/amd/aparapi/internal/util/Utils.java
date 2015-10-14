package com.amd.aparapi.internal.util;

import java.util.*;
import com.amd.aparapi.internal.writer.*;
import com.amd.aparapi.internal.writer.ScalaParameter.DIRECTION;

/**
 * This utility class encapsulates the necessary actions required when processing and generating the kernel.
 */
public class Utils {
	public static enum METHODTYPE {
		UNKNOWN,
		VAR_ACCESS,
		STATUS_CHECK
	}

	static private final Map<String, Map<String, METHODTYPE>> hardCodedClasses = 
		new HashMap<String, Map<String, METHODTYPE>>(); 

	static {
		LinkedHashMap<String, METHODTYPE> tuple2Methods = new LinkedHashMap<String, METHODTYPE>();
		tuple2Methods.put("_1", METHODTYPE.VAR_ACCESS);
		tuple2Methods.put("_2", METHODTYPE.VAR_ACCESS);
		hardCodedClasses.put("scala/Tuple2", tuple2Methods);

		LinkedHashMap<String, METHODTYPE> blazeBroadcastMethods = new LinkedHashMap<String, METHODTYPE>();
		blazeBroadcastMethods.put("value", METHODTYPE.VAR_ACCESS);
		hardCodedClasses.put("org/apache/spark/blaze/BlazeBroadcast", blazeBroadcastMethods);

	 	LinkedHashMap<String, METHODTYPE> iterMethods = new LinkedHashMap<String, METHODTYPE>();
		iterMethods.put("hasNext", METHODTYPE.STATUS_CHECK);
		iterMethods.put("next", METHODTYPE.VAR_ACCESS);
		hardCodedClasses.put("scala/collection/Iterator", iterMethods);
	}

	public static String cleanClassName(String clazz) {
		String tname = clazz.replace('.', '/').replace(";", "").replace(" ", "");
		if (tname.startsWith("L"))
			tname = tname.substring(1);
		return tname;
	}

	public static boolean isPrimitive(String type) {
		if (type.startsWith("I") || type.startsWith("F") || 
				type.startsWith("D") ||type.startsWith("J"))
			return true;
		else
			return false;
	}

	public static String mapPrimitiveType(String type) {
		String newType = "";

		if (type.startsWith("["))
			newType = " * ";

		if (type.equals("I"))
			newType = newType + "int";
		else if (type.equals("F"))
			newType = newType + "float";
		else if (type.equals("D"))
			newType = newType + "double";
		else if (type.equals("J"))
			newType = newType + "long";

		return newType;
	}

	public static boolean isHardCodedClass(String name) {
		// TODO: DenseVector, etc
		String tname = cleanClassName(name);
		if(hardCodedClasses.containsKey(tname))
			return true;
		else
			return false;
	}

	public static Set<String> getHardCodedClassMethods(String clazz) {
		String tname = cleanClassName(clazz);
		if (hardCodedClasses.containsKey(tname))
			return (hardCodedClasses.get(tname).keySet());
		else
			return null;
	}

	public static METHODTYPE getHardCodedClassMethodUsage(String clazz, String methodName) {
		String tname = cleanClassName(clazz);
		if (hardCodedClasses.containsKey(tname)) {
			return hardCodedClasses.get(tname).get(methodName);
		}
		else
			return METHODTYPE.UNKNOWN;
	}

	public static String getHardCodedClassMethod(String clazz, int idx) {
		String tname = cleanClassName(clazz);
		if (hardCodedClasses.containsKey(tname)) {
			ArrayList<String> methodList = new ArrayList<String>(hardCodedClasses.get(tname).keySet());
			return methodList.get(idx);
		}
		else
			return null;
	}

	public static int getHardCodedClassMethodNum(String clazz) {
		String tname = cleanClassName(clazz);
		if (hardCodedClasses.containsKey(tname))
			return (hardCodedClasses.get(tname).size());
		return 0;
	}

	public static boolean hasMethod(String clazz, String methodName) {
		String clazzName = cleanClassName(clazz);
		if(!hardCodedClasses.containsKey(clazzName))
			return false;
		else {
			for (String s: hardCodedClasses.get(clazzName).keySet()) {
				if (methodName.contains(s))
					return true;
			}
			return false;
		}
	}

	public static String cleanMethodName(String clazz, String methodName) {
		String clazzName = cleanClassName(clazz);
		if(!hardCodedClasses.containsKey(clazzName))
			return null;
		else {
			for (String s: hardCodedClasses.get(clazzName).keySet()) {
				if (methodName.contains(s))
					return s;
			}
			return null;
		}
	}

	public static String addHardCodedFieldTypeMapping(String clazz) {
		String clazzName = cleanClassName(clazz);
		if(!hardCodedClasses.containsKey(clazzName))
			return clazzName;
		else {
			boolean first = true;
			Map<String, METHODTYPE> modeledClazz = hardCodedClasses.get(clazzName);
			clazzName += "<";
			for (Map.Entry<String, METHODTYPE> field: modeledClazz.entrySet()) {
				if (field.getValue() == METHODTYPE.VAR_ACCESS) {
					if (!first)
						clazzName += ",";
					clazzName += field.getKey();
					first = false;
				}
			}
			clazzName += ">";
			return clazzName;
		}
	}

	public static ScalaParameter createScalaParameter(String signature, String name, DIRECTION dir) {
		ScalaParameter param = null;

		if (signature.contains("scala/Tuple2"))
			param = new ScalaTuple2Parameter(signature, name, dir);
		else if (signature.startsWith("["))
			param = new ScalaArrayParameter(signature, name, dir);
		else
			param = new ScalaScalarParameter(signature, name, dir);

		return param;
	}
}
