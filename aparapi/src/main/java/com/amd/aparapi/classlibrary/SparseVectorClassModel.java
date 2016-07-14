package com.amd.aparapi.classlibrary;

import java.util.*;
import com.amd.aparapi.internal.model.CustomizedClassModel;
import com.amd.aparapi.internal.model.CustomizedMethodModel;
import com.amd.aparapi.internal.model.MethodModel.METHODTYPE;
import com.amd.aparapi.internal.model.CustomizedFieldModel;

public class SparseVectorClassModel extends CustomizedClassModel {

	public SparseVectorClassModel() {
		super("org.apache.spark.mllib.linalg.SparseVector", null);

		addField(new CustomizedFieldModel("indices", "int []", 0));
		addField(new CustomizedFieldModel("values", "double []", 1));
		addField(new CustomizedFieldModel("size", "int", 2));

		CustomizedMethodModel<?> get_initMethod = new CustomizedMethodModel<SparseVectorClassModel>(
			this, "<init>", METHODTYPE.CONSTRUCTOR) {

			@Override
			public String getReturnType(SparseVectorClassModel clazzModel) {
				return null;
			}

			@Override
			public Map<String, String> getArgs(SparseVectorClassModel clazzModel) {
				Map<String, String> args = new HashMap<String, String>();
				args.put("v", "double *");
				args.put("i", "int *");
				return args;
			}

			@Override
			public String getBody(SparseVectorClassModel clazzModel) {
				return (
					getFieldModel("values").genAssign("v") + ";\n  " +
					getFieldModel("indices").genAssign("i") + ";"
				);
			}
		};
		addMethod(get_initMethod);
	
		CustomizedMethodModel<?> getSizeMethod = new CustomizedMethodModel<SparseVectorClassModel>(
			this, "size", METHODTYPE.GETTER) {

			@Override
			public String getReturnType(SparseVectorClassModel clazzModel) {
				return "int";
			}

			@Override
			public Map<String, String> getArgs(SparseVectorClassModel clazzModel) {
				return null;
			}

			@Override
			public String getBody(SparseVectorClassModel clazzModel) {
				return "return " + getFieldModel("size").genAccess() + ";";
			}
		};
		addMethod(getSizeMethod, getFieldModel("size"));
/*
		CustomizedMethodModel<?> getIndicesMethod = new CustomizedMethodModel<SparseVectorClassModel>(
			this, "indices", METHODTYPE.GETTER) {

			@Override
			public String getReturnType(SparseVectorClassModel clazzModel) {
				return "int *";
			}

			@Override
			public Map<String, String> getArgs(SparseVectorClassModel clazzModel) {
				return null;
			}

			@Override
			public String getBody(SparseVectorClassModel clazzModel) {
				return "return " + getFieldModel("indices").genAccess() + ";";
			}
		};
		addMethod(getIndicesMethod);


		CustomizedMethodModel<?> getValueMethod = new CustomizedMethodModel<SparseVectorClassModel>(
			this, "values", METHODTYPE.GETTER) {
			@Override
			public String getReturnType(SparseVectorClassModel clazzModel) {
				return "double *";
			}

			@Override
			public Map<String, String> getArgs(SparseVectorClassModel clazzModel) {
				return null;
			}

			@Override
			public String getBody(SparseVectorClassModel clazzModel) {
				return "return " + getFieldModel("values").genAccess() + ";";
			}	
		};
		addMethod(getValueMethod);
*/
	}
}
