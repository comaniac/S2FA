package com.amd.aparapi.classlibrary;

import java.util.*;
import com.amd.aparapi.internal.model.CustomizedClassModel;
import com.amd.aparapi.internal.model.CustomizedMethodModel;
import com.amd.aparapi.internal.model.MethodModel.METHODTYPE;
import com.amd.aparapi.internal.model.CustomizedFieldModel;

public class LabeledPointClassModel extends CustomizedClassModel {

	public LabeledPointClassModel() {
		super("org.apache.spark.mllib.regression.LabeledPoint", null);

		addField(new CustomizedFieldModel("label", "double", 0));
		addField(new CustomizedFieldModel("features", "org.apache.spark.mllib.linalg.Vector", 1));

		CustomizedMethodModel<?> getLabelMethod = new CustomizedMethodModel<LabeledPointClassModel>(
			this, "label", METHODTYPE.GETTER) {

			@Override
			public String getReturnType(LabeledPointClassModel clazzModel) {
				return "double";
			}

			@Override
			public Map<String, String> getArgs(LabeledPointClassModel clazzModel) {
				return null;
			}

			@Override
			public String getBody(LabeledPointClassModel clazzModel) {
				return "return " + getFieldModel("label").genAccess() + ";";
			}
		};
		addMethod(getLabelMethod, getFieldModel("label"));

		CustomizedMethodModel<?> getFeatureMethod = new CustomizedMethodModel<LabeledPointClassModel>(
			this, "features", METHODTYPE.GETTER) {

			@Override
			public String getReturnType(LabeledPointClassModel clazzModel) {
				return "org.apache.spark.mllib.linalg.Vector";
			}

			@Override
			public Map<String, String> getArgs(LabeledPointClassModel clazzModel) {
				return null;
			}

			@Override
			public String getBody(LabeledPointClassModel clazzModel) {
				return "return " + getFieldModel("features").genAccess() + ";";
			}
		};
		addMethod(getFeatureMethod, getFieldModel("features"));
	}
}
