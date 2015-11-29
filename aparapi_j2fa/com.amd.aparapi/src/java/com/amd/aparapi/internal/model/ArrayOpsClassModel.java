package com.amd.aparapi.internal.model;

import java.util.*;
import com.amd.aparapi.internal.model.HardCodedMethodModel.METHODTYPE;
import com.amd.aparapi.internal.writer.BlockWriter;

public class ArrayOpsClassModel extends HardCodedClassModel {

	public ArrayOpsClassModel() {
		super("scala/collection/mutable/ArrayOps");
		arrayBasedOrNot = true;
		methods.put("iterator", new thisHardCodedMethodModel("iterator", METHODTYPE.VAR_ACCESS));
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
			if (getMethodName().equals("iterator"))
				return "";
			else
				throw new RuntimeException("ArrayOps has no method " + getMethodName());
		}

		public String getDeclareString(String varName) {
			return null;
		}

	}
}
