package com.amd.aparapi.internal.model;

import java.util.*;
import com.amd.aparapi.internal.model.HardCodedMethodModel.METHODTYPE;

public class BlazeBroadcastClassModel extends HardCodedClassModel {
	public BlazeBroadcastClassModel() {
		super("org/apache/spark/blaze/BlazeBroadcast");
		arrayBasedOrNot = false;
		methods.put("value", new thisHardCodedMethodModel("value", METHODTYPE.VAR_ACCESS));
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
		return className.contains("BlazeBroadcast");
	}

	@Override
	public String toString() {
		return "org/apache/spark/blaze/BlazeBroadcast";
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
			if (methodType == METHODTYPE.VAR_ACCESS)
				return "";
			else
				return null;
		}

		public String getDeclareString(String varName) {
			return "";
		}
	}
}
