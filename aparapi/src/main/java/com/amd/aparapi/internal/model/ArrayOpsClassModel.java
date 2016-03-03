package com.amd.aparapi.internal.model;

import java.util.*;
import com.amd.aparapi.internal.model.MethodModel.METHODTYPE;
import com.amd.aparapi.internal.writer.BlockWriter;

public class ArrayOpsClassModel extends HardCodedClassModel {

	public ArrayOpsClassModel() {
		super("scala/collection/mutable/ArrayOps");
		arrayBasedOrNot = true;
		methods.put("iterator", new thisHardCodedMethodModel("iterator", METHODTYPE.GETTER));
	}

	@Override
	public String getDescriptor() {
		return null;
	}

	@Override
	public List<String> getNestedTypeDescs() {
		return null;
	}

	@Override
	public boolean classNameMatches(String className) {
		return className.contains("ArrayOps");
	}

	@Override
	public String toString() {
		return "scala/collection/mutable/ArrayOps";
	}

	@Override
	public String getMangledClassName() {
		return toString();
	}

	public class thisHardCodedMethodModel extends HardCodedMethodModel {
		public thisHardCodedMethodModel(String name, METHODTYPE methodType) {
			super(name, methodType);
		}

		public String getAccessString(String varName) {
			if (getName().equals("iterator"))
				return "";
			else
				throw new RuntimeException("ArrayOps has no method " + getName());
		}

		public String getDeclareString(String varName) {
			return null;
		}

	}
}
