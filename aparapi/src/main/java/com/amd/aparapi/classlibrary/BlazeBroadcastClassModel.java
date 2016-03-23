package com.amd.aparapi.classlibrary;

import java.util.*;
import com.amd.aparapi.internal.model.CustomizedClassModel;
import com.amd.aparapi.internal.model.CustomizedMethodModel;
import com.amd.aparapi.internal.model.MethodModel.METHODTYPE;
import com.amd.aparapi.internal.model.CustomizedFieldModel;

public class BlazeBroadcastClassModel extends CustomizedClassModel {

	public BlazeBroadcastClassModel() {
		this(new TypeParameters(Arrays.asList("void")));
	}

	public BlazeBroadcastClassModel(TypeParameters params) {
		super("org.apache.spark.blaze.BlazeBroadcast", params);

		addField(new CustomizedFieldModel(params.get(0), "value", 0));
		
		CustomizedMethodModel<?> getValueMethod = new CustomizedMethodModel<BlazeBroadcastClassModel>(
			this, "value", METHODTYPE.GETTER) {
			@Override
			public String getReturnType(BlazeBroadcastClassModel clazzModel) {
				return clazzModel.getTypeParam(0);
			}

			@Override
			public ArrayList<String> getArgs(BlazeBroadcastClassModel clazzModel) {
				return null;
			}

			@Override
			public String getBody(BlazeBroadcastClassModel clazzModel) {
				return ("return " + getFieldModel("value").genAccess() + ";\n");
			}
		};
//		addMethod(getValueMethod, getFieldModel("value"));
		addMethod(getValueMethod);
	}
}
