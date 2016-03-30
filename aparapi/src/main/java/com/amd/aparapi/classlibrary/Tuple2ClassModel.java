package com.amd.aparapi.classlibrary;

import java.util.*;
import com.amd.aparapi.internal.model.CustomizedClassModel;
import com.amd.aparapi.internal.model.CustomizedMethodModel;
import com.amd.aparapi.internal.model.MethodModel.METHODTYPE;
import com.amd.aparapi.internal.model.CustomizedFieldModel;

public class Tuple2ClassModel extends CustomizedClassModel {

	public Tuple2ClassModel() {
		this(new TypeParameters(Arrays.asList("void", "void")));
	}

	public Tuple2ClassModel(TypeParameters params) {
		super("scala.Tuple2", params);

		addField(new CustomizedFieldModel(params.get(0), "v1", 0));
		addField(new CustomizedFieldModel(params.get(1), "v2", 1));

		CustomizedMethodModel<?> get_initMethod = new CustomizedMethodModel<Tuple2ClassModel>(
			this, "<init>", METHODTYPE.CONSTRUCTOR) {

			@Override
			public String getReturnType(Tuple2ClassModel clazzModel) {
				return null;
			}

			@Override
			public ArrayList<String> getArgs(Tuple2ClassModel clazzModel) {
				ArrayList<String> args = new ArrayList<String>();
				args.add(clazzModel.getTypeParam(0) + " n1");
				args.add(clazzModel.getTypeParam(1) + " n2");

				return args;
			}

			@Override
			public String getBody(Tuple2ClassModel clazzModel) {
				return (
					getFieldModel("v1").genAccess() + " = n1;\n  " + 
					getFieldModel("v2").genAccess() + " = n2;"
				);
			}

		};
		addMethod(get_initMethod);
	
		CustomizedMethodModel<?> get_1Method = new CustomizedMethodModel<Tuple2ClassModel>(
			this, "_1", METHODTYPE.GETTER) {

			@Override
			public String getReturnType(Tuple2ClassModel clazzModel) {
				return clazzModel.getTypeParam(0);
			}

			@Override
			public ArrayList<String> getArgs(Tuple2ClassModel clazzModel) {
				return null;
			}

			@Override
			public String getBody(Tuple2ClassModel clazzModel) {
				return "return " + getFieldModel("v1").genAccess() + ";";
			}
		};
		addMethod(get_1Method, getFieldModel("v1"));

		CustomizedMethodModel<?> get_2Method = new CustomizedMethodModel<Tuple2ClassModel>(
			this, "_2", METHODTYPE.GETTER) {

			@Override
			public String getReturnType(Tuple2ClassModel clazzModel) {
				return clazzModel.getTypeParam(1);
			}

			@Override
			public ArrayList<String> getArgs(Tuple2ClassModel clazzModel) {
				return null;
			}

			@Override
			public String getBody(Tuple2ClassModel clazzModel) {
				return "return " + getFieldModel("v2").genAccess() + ";";
			}
		};
		addMethod(get_2Method, getFieldModel("v2"));

	}
}
