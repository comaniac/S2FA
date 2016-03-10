package com.amd.aparapi.classlibrary;

import java.util.*;
import com.amd.aparapi.internal.model.CustomizedClassModel;
import com.amd.aparapi.internal.model.CustomizedMethodModel;
import com.amd.aparapi.internal.model.MethodModel.METHODTYPE;
import com.amd.aparapi.internal.model.CustomizedFieldModel;

public class SparseVectorClassModel extends CustomizedClassModel {

	public SparseVectorClassModel() {
		super("org.apache.spark.mllib.linalg.SparseVector", null);

		addField(new CustomizedFieldModel("int []", "indices", 0));
		addField(new CustomizedFieldModel("double []", "values", 1));
		addField(new CustomizedFieldModel("int", "size", 2));
		
		CustomizedMethodModel<?> getSizeMethod = new CustomizedMethodModel<SparseVectorClassModel>(
			this, "size", METHODTYPE.GETTER) {

			@Override
			public String getReturnType(SparseVectorClassModel clazzModel) {
				return "int";
			}

			@Override
			public ArrayList<String> getArgs(SparseVectorClassModel clazzModel) {
				return null;
			}

			@Override
			public String getBody(SparseVectorClassModel clazzModel) {
				return "return " + getFieldModel("size").genAccess() + ";";
			}
		};
		addMethod(getSizeMethod);

		CustomizedMethodModel<?> getIndicesMethod = new CustomizedMethodModel<SparseVectorClassModel>(
			this, "indices", METHODTYPE.GETTER) {

			@Override
			public String getReturnType(SparseVectorClassModel clazzModel) {
				return "int []";
			}

			@Override
			public ArrayList<String> getArgs(SparseVectorClassModel clazzModel) {
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
				return "double []";
			}

			@Override
			public ArrayList<String> getArgs(SparseVectorClassModel clazzModel) {
				return null;
			}

			@Override
			public String getBody(SparseVectorClassModel clazzModel) {
				return "return " + getFieldModel("values").genAccess() + ";";
			}	
		};
		addMethod(getValueMethod);
	}
}
