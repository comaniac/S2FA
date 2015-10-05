package com.amd.aparapi.internal.util;

import java.util.*;

/**
 * This utility class encapsulates the necessary actions required when processing and generating the kernel.
 */
public class Utils {
	public static enum METHODTYPE {
		VAR_ACCESS,
		STATUS_CHECK
	}

	static private final Map<String, Map<String, METHODTYPE>> transformedClasses = 
		new HashMap<String, Map<String, METHODTYPE>>(); 

	static {
		LinkedHashMap<String, METHODTYPE> tuple2Methods = new LinkedHashMap<String, METHODTYPE>();
		tuple2Methods.put("_1", METHODTYPE.VAR_ACCESS);
		tuple2Methods.put("_2", METHODTYPE.VAR_ACCESS);
		transformedClasses.put("scala/Tuple2", tuple2Methods);

		LinkedHashMap<String, METHODTYPE> blazeBroadcastMethods = new LinkedHashMap<String, METHODTYPE>();
		blazeBroadcastMethods.put("value", METHODTYPE.VAR_ACCESS);
		transformedClasses.put("org/apache/spark/blaze/BlazeBroadcast", blazeBroadcastMethods);

	 	LinkedHashMap<String, METHODTYPE> iterMethods = new LinkedHashMap<String, METHODTYPE>();
		iterMethods.put("hasNext", METHODTYPE.STATUS_CHECK);
		iterMethods.put("next", METHODTYPE.VAR_ACCESS);
		transformedClasses.put("scala/collection/Iterator", iterMethods);
	}

	public static String cleanClassName(String clazz) {
		String tname = clazz.replace('.', '/').replace(";", "");
		if (tname.startsWith("L"))
			tname = tname.substring(1);
		return tname;
	}

	public static boolean isTransformedClass(String name) {
		// TODO: DenseVector, etc
		String tname = cleanClassName(name);
		if(transformedClasses.containsKey(tname))
			return true;
		else
			return false;
	}

	public static Set<String> getTransformedClassMethods(String clazz) {
		String tname = cleanClassName(clazz);
		if (transformedClasses.containsKey(tname))
			return (transformedClasses.get(tname).keySet());
		else
			return null;
	}

	public static String getTransformedClassMethod(String clazz, int idx) {
		String tname = cleanClassName(clazz);
		if (transformedClasses.containsKey(tname)) {
			ArrayList<String> methodList = new ArrayList<String>(transformedClasses.get(tname).keySet());
			return methodList.get(idx);
		}
		else
			return null;
	}

	public static int getTransformedClassMethodNum(String clazz) {
		String tname = cleanClassName(clazz);
		if (transformedClasses.containsKey(tname))
			return (transformedClasses.get(tname).size());
		return 0;
	}

	public static boolean hasMethod(String clazz, String methodName) {
		String clazzName = cleanClassName(clazz);
		if(!transformedClasses.containsKey(clazzName))
			return false;
		else {
			for (String s: transformedClasses.get(clazzName).keySet()) {
				if (methodName.contains(s))
					return true;
			}
			return false;
		}
	}

	public static String cleanMethodName(String clazz, String methodName) {
		String clazzName = cleanClassName(clazz);
		if(!transformedClasses.containsKey(clazzName))
			return null;
		else {
			for (String s: transformedClasses.get(clazzName).keySet()) {
				if (methodName.contains(s))
					return s;
			}
			return null;
		}
	}

	public static String addTransformedFieldTypeMapping(String clazz) {
		String clazzName = cleanClassName(clazz);
		if(!transformedClasses.containsKey(clazzName))
			return clazzName;
		else {
			boolean first = true;
			Map<String, METHODTYPE> modeledClazz = transformedClasses.get(clazzName);
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
}
