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

        addField(new CustomizedFieldModel("v1", this.getTypeParam(0), 0));
        addField(new CustomizedFieldModel("v2", this.getTypeParam(1), 1));

        CustomizedMethodModel<?> get_initMethod =
            new CustomizedMethodModel<Tuple2ClassModel>(
        this, "<init>", METHODTYPE.CONSTRUCTOR) {

            @Override
            public String getReturnType(Tuple2ClassModel clazzModel) {
                return null;
            }

            @Override
            public Map<String, String> getArgs(Tuple2ClassModel clazzModel) {
                Map<String, String> args = new HashMap<String, String>();
                args.put("n1", clazzModel.getTypeParam(0));
                args.put("n2", clazzModel.getTypeParam(1));

                return args;
            }

            @Override
            public String getBody(Tuple2ClassModel clazzModel) {
                String stmt = "";
                if (getFieldModel("v1").isArray()) {
                    if (getFieldModel("v1").knowArrLength())
                        stmt += getFieldModel("v1").genMemcpy("n1");
                    else
                        throw new RuntimeException("Error: v1 array has to " +
                                                   "have specific length.");
                } else
                    stmt += getFieldModel("v1").genAssign("n1");
                stmt += ";\n  ";

                if (getFieldModel("v2").isArray()) {
                    if (getFieldModel("v2").knowArrLength())
                        stmt += getFieldModel("v2").genMemcpy("n2");
                    else
                        throw new RuntimeException("Error: v2 array has to " +
                                                   "have specific length.");
                } else
                    stmt += getFieldModel("v2").genAssign("n2");
                stmt += ";";

                return stmt;
            }

        };
        addMethod(get_initMethod);

        CustomizedMethodModel<?> get_1Method =
            new CustomizedMethodModel<Tuple2ClassModel>(
        this, "_1", METHODTYPE.GETTER) {

            @Override
            public String getReturnType(Tuple2ClassModel clazzModel) {
                return clazzModel.getTypeParam(0);
            }

            @Override
            public Map<String, String> getArgs(Tuple2ClassModel clazzModel) {
                return null;
            }

            @Override
            public String getBody(Tuple2ClassModel clazzModel) {
                return "return " + getFieldModel("v1").genAccess() + ";";
            }
        };
        addMethod(get_1Method, getFieldModel("v1"));

        CustomizedMethodModel<?> get_2Method =
            new CustomizedMethodModel<Tuple2ClassModel>(
        this, "_2", METHODTYPE.GETTER) {

            @Override
            public String getReturnType(Tuple2ClassModel clazzModel) {
                return clazzModel.getTypeParam(1);
            }

            @Override
            public Map<String, String> getArgs(Tuple2ClassModel clazzModel) {
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
