package com.amd.aparapi.classlibrary;

import java.util.*;
import com.amd.aparapi.internal.model.CustomizedClassModel;
import com.amd.aparapi.internal.model.CustomizedMethodModel;
import com.amd.aparapi.internal.model.MethodModel.METHODTYPE;
import com.amd.aparapi.internal.model.CustomizedFieldModel;

public class DenseVectorClassModel extends CustomizedClassModel {

	public DenseVectorClassModel() {
		super("org.apache.spark.mllib.linalg.DenseVector", null);

		addField(new CustomizedFieldModel("size", "int", 0));
		addField(new CustomizedFieldModel("values", "double []", 1));
		
		CustomizedMethodModel<?> getSizeMethod = new CustomizedMethodModel<DenseVectorClassModel>(
			this, "size", METHODTYPE.GETTER) {

			@Override
			public String getReturnType(DenseVectorClassModel clazzModel) {
				return "int";
			}

			@Override
			public Map<String, String> getArgs(DenseVectorClassModel clazzModel) {
				return null;
			}

			@Override
			public String getBody(DenseVectorClassModel clazzModel) {
				return "return " + getFieldModel("size").genAccess() + ";";
			}
		};
		addMethod(getSizeMethod);

		CustomizedMethodModel<?> getValueMethod = new CustomizedMethodModel<DenseVectorClassModel>(
			this, "apply", METHODTYPE.GETTER) {
			@Override
			public String getReturnType(DenseVectorClassModel clazzModel) {
				return "double";
			}

			@Override
			public Map<String, String> getArgs(DenseVectorClassModel clazzModel) {
				Map<String, String> args = new HashMap<String, String>();
				args.put("index", "int");
				return args;
			}

			@Override
			public String getBody(DenseVectorClassModel clazzModel) {
				return "return " + getFieldModel("values").genArrayElementAccess("index") + ";";
			}	
		};
		addMethod(getValueMethod);
	}
}
