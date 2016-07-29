package com.amd.aparapi.internal.model;

import java.util.*;

import com.amd.aparapi.internal.instruction.InstructionSet.TypeSpec;
import com.amd.aparapi.internal.exception.AparapiException;
import com.amd.aparapi.internal.util.Utils;
import com.amd.aparapi.internal.writer.BlockWriter;

public abstract class CustomizedClassModel extends ClassModel {
	private final String className;
	private final ArrayList<CustomizedMethodModel<?>> methods;
	private final ArrayList<CustomizedFieldModel> fields;
	private final TypeParameters typeParams;
	private final HashMap<String, Integer> method2Param;

	public CustomizedClassModel(String clazzName, TypeParameters typeParams) {
		this.className = clazzName;
		if (typeParams != null)
			this.typeParams = typeParams;
		else
			this.typeParams = new TypeParameters();
		this.methods = new ArrayList<CustomizedMethodModel<?>>();
		this.fields = new ArrayList<CustomizedFieldModel>();
		this.method2Param = new HashMap<String, Integer>();
	}

	// Class related

	@Override
	public String getClassName() {
		return className;
	}

	public String getDescriptor() {
		return className + typeParams.toString();
	}

	private boolean isSubclassOf(String target, String superclass) {
		if (target.equals(superclass))
			return true;

		if (target.startsWith("L") && superclass.equals("Ljava/lang/Object;"))
			return true;

		return false;
	}

	// TODO: Change name to "getClassDeclareCode"
	public String getStructCode() {
		StringBuilder sb = new StringBuilder();
	  sb.append("class " + getMangledClassName());
		if (isDerivedClass())
			sb.append(" : public " + getSuperClazz().getMangledClassName());
		sb.append(" {\n  public:\n");

		// Write fields
	  for (CustomizedFieldModel f : getFieldModels()) {
	  	sb.append("  " + f.getDeclareCode() + ";\n");
/* FIXME: Should be added only when necessary
			if (f.isArray())
				sb.append("  int " + f.getName() + BlockWriter.arrayLengthMangleSuffix + ";\n");
*/
		}
		sb.append("\n");

		// Write methods
		for (CustomizedMethodModel<?> method : getMethods()) {
			String methodDecl = method.getDeclareCode();
			methodDecl = "  " + methodDecl.replace("\n", "\n  ");
			sb.append(methodDecl);
			sb.append("\n");
		}

	  sb.append("};");
	  return sb.toString();
	}

	@Override
	public boolean classNameMatches(String className) {
	  return className.equals(className);
	}

	@Override
	public String getMangledClassName() {
		String name = className.replace('.', '_');
		for (String s : typeParams.list())
			name += "_" + Utils.convertToCType(s);
		name = name.replace("*", "Ary");
		return name;
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

	public List<CustomizedFieldModel> getFieldModels() {
		return fields;
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

	public void addMethod(CustomizedMethodModel<?> method, CustomizedFieldModel field) {
		method.setGetterField(field);
		methods.add(method);
		method2Param.put(method.getName(), new Integer(field.getOffset()));
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
		String topMethodName = _name;
		if (topMethodName.contains("$"))
			topMethodName = topMethodName.substring(0, topMethodName.indexOf("$"));
		for (CustomizedMethodModel<?> method : methods) {
			if (method.getName().equals(topMethodName))
				return method;
		}
		return null;
	}

	@Override
	public MethodModel checkForCustomizedMethods(String name, String desc)
	throws AparapiException {
		return getMethodModel(name, desc);
	}

	public String getMethodDeclareCode(String methodName) {
		CustomizedMethodModel<?> method = getCustomizedMethod(methodName);
		if (method != null)
			return method.getDeclareCode();
		else
			return null;
	}

	public String getMethod2ParamMapping() {
		boolean first = true;
		String s = "<";
		for (String method : method2Param.keySet()) {
			if (!first)
				s += ",";
			s += method2Param.get(method);
			first = false;
		}
		s += ">";
		return s;
	}

	// Type parameter related

	public TypeParameters getTypeParams() {
		return typeParams;
	}

	public String getTypeParam(int idx) {
		return typeParams.get(idx);
	}

	public boolean hasTypeParams() {
		if (typeParams.size() == 0)
			return false;
		return true;
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

		public List<String> list() {
			return typeParams;
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
			sb.append("<");
			for (String p : typeParams)
				sb.append(p + ",");
			sb.append(">");
			return sb.toString();
		}
	}
}
