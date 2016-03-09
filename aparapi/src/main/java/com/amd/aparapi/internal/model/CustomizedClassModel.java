package com.amd.aparapi.internal.model;

import java.util.*;

import com.amd.aparapi.internal.instruction.InstructionSet.TypeSpec; // FIXME: How to use?
import com.amd.aparapi.internal.exception.AparapiException;

public abstract class CustomizedClassModel extends ClassModel {
	private final String className;
	private final ArrayList<CustomizedMethodModel<?>> methods;
	private final ArrayList<CustomizedFieldModel> fields;
	private final TypeParameters typeParams;

	public CustomizedClassModel(String clazzName, TypeParameters typeParams) {
		this.className = clazzName;
		if (typeParams != null)
			this.typeParams = typeParams;
		else
			this.typeParams = new TypeParameters();
		this.methods = new ArrayList<CustomizedMethodModel<?>>();
		this.fields = new ArrayList<CustomizedFieldModel>();
	}

	// Class related

	public String getClassName() {
		return className;
	}

	private boolean isSubclassOf(String target, String superclass) {
		if (target.equals(superclass))
			return true;

		if (target.startsWith("L") && superclass.equals("Ljava/lang/Object;"))
			return true;

		return false;
	}

	@Override
	public boolean classNameMatches(String className) {
	  return className.equals(className);
	}

	@Override
	public String getMangledClassName() {
		return className.replace('.', '_');
	}

	// Field related

	public void addField(CustomizedFieldModel field) {
		fields.add(field);
	}

	public CustomizedFieldModel getFieldModel(String _name) {
		for (CustomizedFieldModel field : fields) {
		  if (field.getName().equals(_name))
		    return field;
		}
		return null;		
	}

	@Override
	public ArrayList<FieldNameInfo> getStructMembers() {
		ArrayList<FieldNameInfo> members = new ArrayList<FieldNameInfo>();
		for (CustomizedFieldModel f : fields)
			members.add(new FieldNameInfo(f.getName(), f.getShortType(), f.getType()));
		return members;
	}

	@Override
	public List<FieldDescriptor> getStructMemberInfo() {
		List<FieldDescriptor> members = new LinkedList<FieldDescriptor>();
		int id = 0;
		for (CustomizedFieldModel f : fields) {
			members.add(new FieldDescriptor(id, f.getTypeSpec(), f.getName(), f.getOffset()));
			id += 1;
		}
  	return members;
	}

	// Method related

	public void addMethod(CustomizedMethodModel<?> method) {
		methods.add(method);
	}

	public ArrayList<CustomizedMethodModel<?>> getMethods() {
		return methods;
	}

	public int getMethodNum() {
		return methods.size();
	}

	public boolean hasMethod(String methodName) {
		if (getCustomizedMethod(methodName) != null)
			return true;
		return false;
	}

	public String getMethodNameByIdx(int idx) {
		return methods.get(idx).getName();
	}

	@Override
	public MethodModel getMethodModel(String _name, String _signature)
		throws AparapiException {
		return null;
	}

	// TODO: Method overloading (consider sig as well)
	public CustomizedMethodModel<?> getCustomizedMethod(String _name) {
		for (CustomizedMethodModel<?> method : methods) {
			if (method.getName().equals(_name))
				return method;
		}
		return null;
	}

	// FIXME: To be renamed in ClassModel
	@Override
	public MethodModel checkForHardCodedMethods(String name, String desc)
	throws AparapiException {
		return getMethodModel(name, desc);
	}

	public String getMethodDeclareCode(String varName, String methodName) {
		CustomizedMethodModel<?> method = getCustomizedMethod(methodName);
		if (method != null)
			return method.getDeclareCode(varName);
		else
			return null;
	}

	// Type parameter related

	public TypeParameters getTypeParams() {
		return typeParams;
	}

	public String getTypeParam(int idx) {
		return typeParams.get(idx);
	}

	public static class TypeParameters implements Comparable<TypeParameters>, Iterable<String> {
		private final List<String> typeParams = new LinkedList<String>();

		public TypeParameters(String... typeParams) {
			for (String d : typeParams)
				this.typeParams.add(d);
		}

		public TypeParameters(List<String> typeParams) {
			for (String d : typeParams)
				this.typeParams.add(d);
		}

		public String get(int index) {
			return typeParams.get(index);
		}

		public int size() {
			return typeParams.size();
		}

		@Override
		public Iterator<String> iterator() {
			return typeParams.iterator();
		}

		@Override
		public boolean equals(Object obj) {
			if (obj instanceof TypeParameters) {
				TypeParameters other = (TypeParameters)obj;
				Iterator<String> otherIter = other.typeParams.iterator();
				Iterator<String> thisIter = typeParams.iterator();
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
			return typeParams.get(0).compareTo(other.typeParams.get(0));
		}

		@Override
		public int hashCode() {
			return typeParams.size();
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("[ ");
			for (String p : typeParams)
				sb.append(p + " ");
			sb.append("]");
			return sb.toString();
		}
	}
}
