package com.amd.aparapi.internal.util;

import java.util.*;

/**
 * This utility class encapsulates the necessary actions required when processing and generating the kernel.
 */
public class Utils {

	static private final Map<String, String []> transformedClasses = new HashMap<String, String []>(); 
	static {
		transformedClasses.put("scala/Tuple2", new String [] {"_1", "_2"});
		transformedClasses.put("org/apache/spark/blaze/BlazeBroadcast", new String [] {"value"});
	}

	public static boolean isTransformedClass(String name) {
		// TODO: DenseVector, etc
		String tname = name.replace('.', '/').replace(";", "");
		if (tname.startsWith("L"))
			tname = tname.substring(1);
		if(transformedClasses.containsKey(tname))
			return true;
		else
			return false;
	}

	public static String getTransformedClassField(String clazz, int idx) {
		String tname = clazz.replace('.', '/').replace(";", "");
		if (tname.startsWith("L"))
			tname = tname.substring(1);

		if (transformedClasses.containsKey(tname))
			return (transformedClasses.get(tname))[idx];
		else
			return "";
	}

	public static String [] getTransformedClassFields(String clazz) {
		String tname = clazz.replace('.', '/').replace(";", "");
		if (tname.startsWith("L"))
			tname = tname.substring(1);

		if (transformedClasses.containsKey(tname))
			return (transformedClasses.get(tname));
		else
			return null;
	}
}
