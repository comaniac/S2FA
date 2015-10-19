package com.amd.aparapi.internal.util;

import java.util.*;
import com.amd.aparapi.internal.model.HardCodedMethodModel.METHODTYPE;
import com.amd.aparapi.internal.model.*;
import com.amd.aparapi.internal.writer.*;
import com.amd.aparapi.internal.writer.ScalaParameter.DIRECTION;

/**
 * This utility class encapsulates the necessary actions required when processing and generating the kernel.
 */
public class Utils {

	static private final Map<String, HardCodedClassModel> hardCodedClasses = 
		new HashMap<String, HardCodedClassModel>(); 

	static {
		hardCodedClasses.put("scala/Tuple2", new Tuple2ClassModel());
		hardCodedClasses.put("org/apache/spark/blaze/BlazeBroadcast", new BlazeBroadcastClassModel());
		hardCodedClasses.put("scala/collection/Iterator", new IteratorClassModel());
		hardCodedClasses.put("scala/collection/mutable/ArrayOps", new ArrayOpsClassModel());
	}

	public static String cleanClassName(String clazz) {
		String tname = clazz.replace('.', '/').replace(";", "").replace(" ", "");
		if (tname.contains("$")) // Get rid of inner class names
			tname = tname.substring(0, tname.indexOf("$"));
		if (tname.startsWith("L"))
			tname = tname.substring(1);
		return tname;
	}

	public static String cleanMethodName(String clazz, String methodName) {
		String clazzName = cleanClassName(clazz);
		if(!hardCodedClasses.containsKey(clazzName))
			return null;
		else
			return hardCodedClasses.get(clazzName).getPureMethodName(methodName);
	}

	public static boolean isArrayBasedClass(String clazz) {
		String pClazzName = cleanClassName(clazz);
		if (hardCodedClasses.containsKey(pClazzName))
			return hardCodedClasses.get(pClazzName).isArrayBased();
		else
			return false;
	}

	public static boolean isIteratorClass(String clazz) {
		String pClazzName = cleanClassName(clazz);
		if (hardCodedClasses.containsKey(pClazzName))
			return (hardCodedClasses.get(pClazzName) instanceof IteratorClassModel);
		else
			return false;
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

	public static Set<String> getHardCodedClassMethods(String clazz, METHODTYPE methodType) {
		String tname = cleanClassName(clazz);
		if (hardCodedClasses.containsKey(tname))
			return (hardCodedClasses.get(tname).getMethodNames(methodType));
		else
			return null;
	}

	public static METHODTYPE getHardCodedClassMethodUsage(String clazz, String methodName) {
		String tname = cleanClassName(clazz);
		if (hardCodedClasses.containsKey(tname)) {
			return hardCodedClasses.get(tname).getMethodType(methodName);
		}
		else
			return METHODTYPE.UNKNOWN;
	}

	public static String getAccessHardCodedMethodString(String clazz, String methodName, String varName) {
		String pClazzName = cleanClassName(clazz);
		String pMethodName = cleanMethodName(pClazzName, methodName);

		if (hardCodedClasses.containsKey(pClazzName)) {
			if (hardCodedClasses.get(pClazzName).hasMethod(pMethodName)) {
				return hardCodedClasses
								.get(pClazzName)
								.getMethodAccessString(varName, pMethodName);
			}
			else
				throw new RuntimeException("Class " + pClazzName + " has no method " + pMethodName);
		}
		else
			throw new RuntimeException("No hard coded class " + pClazzName);
	}

	public static String getDeclareHardCodedMethodString(String clazz, String methodName, String varName) {
		String pClazzName = cleanClassName(clazz);
		String pMethodName = cleanMethodName(pClazzName, methodName);

		if (hardCodedClasses.containsKey(pClazzName)) {
			if (hardCodedClasses.get(pClazzName).hasMethod(pMethodName)) {
				return hardCodedClasses
								.get(pClazzName)
								.getMethodDeclareString(varName, pMethodName);
			}
			else
				throw new RuntimeException("Class " + pClazzName + " has no method " + pMethodName);
		}
		else
			throw new RuntimeException("No hard coded class " + pClazzName);
	}

	public static String getHardCodedClassMethod(String clazz, int idx) {
		String tname = cleanClassName(clazz);
		if (hardCodedClasses.containsKey(tname)) {
			return hardCodedClasses.get(tname).getMethodNameByIdx(idx);
		}
		else
			return null;
	}

	public static int getHardCodedClassMethodNum(String clazz) {
		String tname = cleanClassName(clazz);
		if (hardCodedClasses.containsKey(tname))
			return (hardCodedClasses.get(tname).getMethodNum());
		return 0;
	}

	public static boolean hasMethod(String clazz, String methodName) {
		String pClazzName = cleanClassName(clazz);
		if(!hardCodedClasses.containsKey(pClazzName))
			return false;
		else {
			String pMethodName = cleanMethodName(pClazzName, methodName);
			return hardCodedClasses.get(pClazzName).hasMethod(methodName);
		}
	}

	public static String addHardCodedFieldTypeMapping(String clazz) {
		String pClazzName = cleanClassName(clazz);
		if(!hardCodedClasses.containsKey(pClazzName))
			return pClazzName;
		else {
			boolean first = true;
			HardCodedClassModel modeledClazz = hardCodedClasses.get(pClazzName);
			pClazzName += "<";
			Map<String, HardCodedMethodModel> modeledClazzMethods = modeledClazz.getMethods();
			for (Map.Entry<String, HardCodedMethodModel> method: modeledClazzMethods.entrySet()) {
				if (method.getValue().getMethodType() == METHODTYPE.VAR_ACCESS) {
					if (!first)
						pClazzName += ",";
					pClazzName += method.getKey();
					first = false;
				}
			}
			pClazzName += ">";
			return pClazzName;
		}
	}

	public static ScalaParameter createScalaParameter(String signature, String name, DIRECTION dir) {
		ScalaParameter param = null;

		if (signature.contains("scala/Tuple2"))
			param = new ScalaTuple2Parameter(signature, name, dir);
		else if (signature.contains("scala/collection/Iterator"))
			param = new ScalaIteratorParameter(signature, name, dir);
		else if (signature.startsWith("["))
			param = new ScalaArrayParameter(signature, name, dir);
		else
			param = new ScalaScalarParameter(signature, name, dir);

		return param;
	}
}
