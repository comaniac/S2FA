package com.amd.aparapi.classlibrary;

import java.util.*;
import com.amd.aparapi.internal.model.CustomizedClassModel;
import com.amd.aparapi.internal.model.CustomizedMethodModel;
import com.amd.aparapi.internal.model.MethodModel.METHODTYPE;
import com.amd.aparapi.internal.model.CustomizedFieldModel;

public class IteratorClassModel extends CustomizedClassModel {

    public IteratorClassModel() {
        this(new TypeParameters(Arrays.asList("void []")));
    }

    public IteratorClassModel(TypeParameters params) {
        super("scala.collection.Iterator", params);

        addField(new CustomizedFieldModel("index", "int", 0));
        addField(new CustomizedFieldModel("length", "int", 1));
        addField(new CustomizedFieldModel("values", params.get(0), 2));

        CustomizedMethodModel<?> getHasNextMethod =
            new CustomizedMethodModel<IteratorClassModel>(
        this, "hasNext", METHODTYPE.CHECKER) {

            @Override
            public String getReturnType(IteratorClassModel clazzModel) {
                return "boolean";
            }

            @Override
            public Map<String, String> getArgs(IteratorClassModel clazzModel) {
                return null;
            }

            @Override
            public String getBody(IteratorClassModel clazzModel) {
                return "return (" + getFieldModel("index").genAccess() + " < " +
                       getFieldModel("length").genAccess() + ");";
            }
        };
        addMethod(getHasNextMethod);

        CustomizedMethodModel<?> getNextMethod =
            new CustomizedMethodModel<IteratorClassModel>(
        this, "next", METHODTYPE.GETTER) {
            @Override
            public String getReturnType(IteratorClassModel clazzModel) {
                return clazzModel.getTypeParam(0);
            }

            @Override
            public Map<String, String> getArgs(IteratorClassModel clazzModel) {
                return null;
            }

            @Override
            public String getBody(IteratorClassModel clazzModel) {
                return (
                           clazzModel.getTypeParam(0) + " val = " +
                           getFieldModel("values")
                           .genArrayElementAccess(
                               getFieldModel("index").genAccess()) + ";\n" +
                           getFieldModel("index")
                           .genAssign(getFieldModel("index").genAccess() + "+ 1") +
                           ";\n" + "return val;"
                       );
            }
        };
        addMethod(getNextMethod);
    }
}
