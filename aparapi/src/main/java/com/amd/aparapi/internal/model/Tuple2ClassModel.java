package com.amd.aparapi.internal.model;

import java.util.*;
import com.amd.aparapi.internal.model.MethodModel.METHODTYPE;

public class Tuple2ClassModel extends HardCodedClassModel {

	public Tuple2ClassModel() {
		super("scala/Tuple2");
		arrayBasedOrNot = false;
		methods.put("<init>", new thisHardCodedMethodModel("<init>", METHODTYPE.CONSTRUCTOR));
		methods.put("_1", new thisHardCodedMethodModel("_1", METHODTYPE.GETTER));
		methods.put("_2", new thisHardCodedMethodModel("_2", METHODTYPE.GETTER));
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
		return className.contains("Tuple2");
	}

	@Override
	public String toString() {
		return "scala/Tuple2";
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
			if (methodType == METHODTYPE.GETTER)
				return name;
			else
				return null;
		}

		public String getDeclareString(String varName) {
			if (methodType == METHODTYPE.GETTER)
				return name;
			else
				return null;
		}

	}
}
