package com.amd.aparapi.internal.model;

import java.util.*;
import com.amd.aparapi.internal.model.MethodModel.METHODTYPE;
import com.amd.aparapi.internal.writer.BlockWriter;

public class IteratorClassModel extends HardCodedClassModel {

	public IteratorClassModel() {
		super("scala/collection/Iterator");
		arrayBasedOrNot = true;
		methods.put("hasNext", new thisHardCodedMethodModel("hasNext", METHODTYPE.CHECKER));
		methods.put("next", new thisHardCodedMethodModel("next", METHODTYPE.GETTER));
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
		return className.contains("Iterator");
	}

	@Override
	public String toString() {
		return "scala/collection/Iterator";
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
				return "[ITER_INC(" + varName + BlockWriter.iteratorIndexSuffix + ", " + 
					varName + BlockWriter.arrayItemLengthMangleSuffix + ")]";
			else if (methodType == METHODTYPE.CHECKER)
				// NOTICE: Only argument can have Iterator type. We don't generate
				// the condition with "this->a__javaArrayLength"
				// Not necessary to write variable name here since it has been written in advance
				return BlockWriter.iteratorIndexSuffix + " < " + 
							 varName + BlockWriter.arrayLengthMangleSuffix;
			else
				return null;
		}

		public String getDeclareString(String varName) {
			if (getName().equals("next"))
				return "";
			else
				return null;
		}

	}
}
