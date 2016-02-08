package com.amd.aparapi.internal.model;

import java.util.*;
import com.amd.aparapi.internal.model.HardCodedMethodModel.METHODTYPE;

import com.amd.aparapi.internal.instruction.InstructionSet.TypeSpec;
import com.amd.aparapi.internal.exception.AparapiException;

public abstract class HardCodedClassModel extends ClassModel {
	protected final LinkedHashMap<String, HardCodedMethodModel> methods;
	protected final TypeParameters paramDescs;
	protected final String clazzName;
	protected boolean arrayBasedOrNot;

	public HardCodedClassModel(String clazzName) {
		this.clazzName = clazzName;
		this.clazz = null;
		this.paramDescs = null;
		this.methods = new LinkedHashMap<String, HardCodedMethodModel>();
		this.arrayBasedOrNot = false;
	}

	public String getClassName() {
		return clazzName;
	}

	public boolean isArrayBased() {
		return arrayBasedOrNot;
	}

	// Method related methods

	public Map<String, HardCodedMethodModel> getMethods() {
		return methods;
	}

	public int getMethodNum() {
		return methods.size();
	}

	public boolean hasMethod(String methodName) {
		return methods.containsKey(methodName);
	}

	public String getPureMethodName(String methodName) {
		for (String pureName : methods.keySet()) {
			if (methodName.contains(pureName))
				return pureName;
		}
		return null;
	}

	public Set<String> getMethodNames(METHODTYPE type) {
		Set<String> methodNames = new HashSet<String>();
		for (String name : methods.keySet()) {
			if (methods.get(name).getMethodType() == type)
				methodNames.add(name);
		}
		return methodNames;
	}

	public String getMethodNameByIdx(int idx) {
		List<String> l = new ArrayList<String>(methods.keySet());
		return l.get(idx);
	}

	public METHODTYPE getMethodType(String methodName) {
		if (methods.containsKey(methodName))
			return methods.get(methodName).getMethodType();
		else
			return METHODTYPE.UNKNOWN;
	}

	public String getMethodAccessString(String varName, String methodName) {
		if (methods.containsKey(methodName))
			return methods.get(methodName).getAccessString(varName);
		else
			return null;
	}

	public String getMethodDeclareString(String varName, String methodName) {
		if (methods.containsKey(methodName))
			return methods.get(methodName).getDeclareString(varName);
		else
			return null;
	}

	public abstract class thisHardCodedMethodModel extends HardCodedMethodModel {
		public thisHardCodedMethodModel(String name, METHODTYPE methodType) {
			super(name, methodType);
		}
		public abstract String getAccessString(String varName);
		public abstract String getDeclareString(String varName);
	}

	// Old methods FIXME

	public TypeParameters getTypeParamDescs() {
		return paramDescs;
	}

	public abstract String getDescriptor();
	public abstract List<String> getNestedTypeDescs();

	@Override
	public MethodModel checkForHardCodedMethods(String name, String desc)
	throws AparapiException {
		return getMethodModel(name, desc);
	}

	private boolean isSubclassOf(String target, String superclass) {
		if (target.equals(superclass))
			return true;

		if (target.startsWith("L") && superclass.equals("Ljava/lang/Object;"))
			return true;

		return false;
	}

	private boolean areSignaturesCompatible(String specific, String broad,
	                                        String lookingForMethodName) {
		if (lookingForMethodName.equals("<init>"))
			return true;

		String specificParams = specific.substring(specific.indexOf('(') + 1);
		specificParams = specificParams.substring(0, specificParams.indexOf(')'));

		String broadParams = broad.substring(broad.indexOf('(') + 1);
		broadParams = broadParams.substring(0, broadParams.indexOf(')'));

		String[] specificParamsSplit = specificParams.split(",");
		String[] broadParamsSplit = broadParams.split(",");

		String specificReturn = specific.substring(specific.lastIndexOf(')') + 1);
		String broadReturn = broad.substring(broad.lastIndexOf(')') + 1);

		if (specificParamsSplit.length != broadParamsSplit.length) return false;

		if (isSubclassOf(specificReturn, broadReturn)) {
			for (int i = 0; i < specificParamsSplit.length; i++) {
				String s = specificParamsSplit[i];
				String b = broadParamsSplit[i];

				if (!isSubclassOf(s, b))
					return false;
			}
			return true;
		} else
			return false;
	}

	@Override
	public MethodModel getMethodModel(String _name, String _signature)
	throws AparapiException {
		return null;
	}

	public static class AllFieldInfo {
		public final String name;
		public final String desc;
		public final String className;
		public int offset;
		public final TypeSpec typ;

		public AllFieldInfo(String name, String desc, String className, int offset) {
			this.name = name;
			this.desc = desc;
			this.className = className;
			this.offset = offset;

			boolean haveTypeSpec = false;
			for (TypeSpec t : TypeSpec.values()) {
				if (t.getShortName().equals(desc))
					haveTypeSpec = true;
			}

			if (haveTypeSpec)
				this.typ = TypeSpec.valueOf(desc);
			else
				this.typ = TypeSpec.O;
		}
	}

	public static class TypeParameters implements Comparable<TypeParameters>, Iterable<String> {
		private final List<String> paramDescs = new LinkedList<String>();

		public TypeParameters(String... paramDescs) {
			for (String d : paramDescs)
				this.paramDescs.add(d);
		}

		public TypeParameters(List<String> paramDescs) {
			for (String d : paramDescs)
				this.paramDescs.add(d);
		}

		public String get(int index) {
			return paramDescs.get(index);
		}

		public int size() {
			return paramDescs.size();
		}

		@Override
		public Iterator<String> iterator() {
			return paramDescs.iterator();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof TypeParameters) {
				TypeParameters other = (TypeParameters)obj;
				Iterator<String> otherIter = other.paramDescs.iterator();
				Iterator<String> thisIter = paramDescs.iterator();
				while (otherIter.hasNext() && thisIter.hasNext()) {
					String otherEle = otherIter.next();
					String thisEle = thisIter.next();
					if (!otherEle.equals(thisEle))
						return false;
				}

				if (otherIter.hasNext() != thisIter.hasNext())
					return false;
				return true;
			}
			return false;
		}

		@Override
		public int compareTo(TypeParameters other) {
			if (this.equals(other)) return 0;
			return paramDescs.get(0).compareTo(other.paramDescs.get(0));
		}

		@Override
		public int hashCode() {
			return paramDescs.size();
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("[ ");
			for (String p : paramDescs)
				sb.append(p + " ");
			sb.append("]");
			return sb.toString();
		}
	}
}
