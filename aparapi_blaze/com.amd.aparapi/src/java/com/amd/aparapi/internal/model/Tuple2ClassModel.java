package com.amd.aparapi.internal.model;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;

import com.amd.aparapi.internal.instruction.InstructionSet.TypeSpec;
import com.amd.aparapi.internal.exception.AparapiException;
import com.amd.aparapi.internal.model.HardCodedMethodModel.MethodDefGenerator;
import com.amd.aparapi.internal.writer.KernelWriter;

public class Tuple2ClassModel extends HardCodedClassModel {
    private static Class<?> clz;
    static {
        try {
            clz = Class.forName("scala.Tuple2");
        } catch (ClassNotFoundException c) {
            throw new RuntimeException(c);
        }
    }

    private Tuple2ClassModel(String firstTypeDesc, String secondTypeDesc,
            List<HardCodedMethodModel> methods, List<AllFieldInfo> fields) {
        super(clz, methods, fields, firstTypeDesc, secondTypeDesc);
        initMethodOwners();
    }

    @Override
    public String getDescriptor() {

      return "Lscala/Tuple2<" + descToName(getFirstTypeDesc()).replace('.', '/') + "," +
        descToName(getSecondTypeDesc()).replace('.', '/') + ">";
    }

    public String getFirstTypeDesc() {
      return paramDescs.get(0);
    }

    public String getSecondTypeDesc() {
      return paramDescs.get(1);
    }

    private static String descToName(String desc) {
        if (desc.startsWith("L")) {
            return desc.substring(1, desc.length() - 1);
        } else {
            return desc;
        }
    }

    public static Tuple2ClassModel create(String firstTypeDesc,
            String secondTypeDesc, boolean isConstructable) {
        final String firstTypeClassName = descToName(firstTypeDesc);
        final String secondTypeClassName = descToName(secondTypeDesc);

        List<AllFieldInfo> fields = new ArrayList<AllFieldInfo>(2);
        fields.add(new AllFieldInfo("_1", firstTypeDesc, firstTypeClassName, -1));
        fields.add(new AllFieldInfo("_2", secondTypeDesc, secondTypeClassName, -1));

        MethodDefGenerator constructorGen = new MethodDefGenerator<Tuple2ClassModel>() {
            private String convertDescToType(final String desc, KernelWriter writer) {
                final String type;
                final String converted = writer.convertType(desc, true);
                if (desc.startsWith("L")) {
                  ClassModel cm = writer.getEntryPoint().getModelFromObjectArrayFieldsClasses(
                      converted.trim(), new ClassModelMatcher() {
                          @Override
                          public boolean matches(ClassModel model) {
                              // No generic types should be allowed for fields of Tuple2s
                              if (model.getClassWeAreModelling().getName().equals(converted.trim())) {
                                  return true;
                              } else {
                                  return false;
                              }
                          }
                      });
                  type = "__global " + cm.getMangledClassName() + " * ";
                } else {
                  type = converted;
                }
                return type;
            }

            @Override
            public String getMethodDef(HardCodedMethodModel method,
                    Tuple2ClassModel classModel, KernelWriter writer) {
                String owner = method.getOwnerClassMangledName();

                final String firstType = convertDescToType(classModel.getFirstTypeDesc(), writer);
                final String secondType = convertDescToType(classModel.getSecondTypeDesc(), writer);

                StringBuilder sb = new StringBuilder();
                sb.append("static __global " + owner + " *" + method.getName() +
                    "(__global " + owner + " *this, " + firstType +
                    " one, " + secondType + " two) {\n");

								// comaniac: Issue #1, we use scalar instead of pointer for kernel argument structure type.
								// It means that we have to assign the value of the Tuple2 input array element to in kernel Tuple2 struct.
								// Restriction: Tuple2 doesn't allow Array type.
								// TODO: Recognize the platform and generate different kernels.
								if (firstType.contains("*"))
									sb.append("   this->_1 = *one;\n");
								else
	                sb.append("   this->_1 = one;\n");
								
								if (secondType.contains("*"))
	                sb.append("   this->_2 = *two;\n");
								else
									sb.append("   this->_2 = two;\n");
                sb.append("   return this;\n");
                sb.append("}");
                return sb.toString();
            }
        };

        List<HardCodedMethodModel> methods = new ArrayList<HardCodedMethodModel>();
        methods.add(new HardCodedMethodModel("_1$mcI$sp", "()" + firstTypeDesc,
              null, true, "_1"));
        methods.add(new HardCodedMethodModel("_2", "()" + secondTypeDesc, null,
              true, "_2"));
        methods.add(new HardCodedMethodModel("_2$mcI$sp", "()" + secondTypeDesc, null,
              true, "_2"));
        if (isConstructable) {
            methods.add(new HardCodedMethodModel("<init>", "(" + firstTypeDesc +
                  secondTypeDesc + ")V", constructorGen, false, null));
        }

        return new Tuple2ClassModel(firstTypeDesc, secondTypeDesc,
            methods, fields);
    }

    @Override
    public List<String> getNestedTypeDescs() {
        List<String> l = new ArrayList<String>(2);
        if (getFirstTypeDesc().startsWith("L")) {
            l.add(getFirstTypeDesc());
        }
        if (getSecondTypeDesc().startsWith("L")) {
            l.add(getSecondTypeDesc());
        }
        return l;
    }

    @Override
    public String getMangledClassName() {
        final String firstTypeClassName = descToName(getFirstTypeDesc());
        final String secondTypeClassName = descToName(getSecondTypeDesc());
        return "scala_Tuple2_" + firstTypeClassName.replace(".", "_") + "_" +
          secondTypeClassName.replace(".", "_");
    }

   @Override
   public boolean classNameMatches(String className) {
      return className.startsWith("scala.Tuple2");
   }

   @Override
   public String toString() {
       return "Tuple2[" + getFirstTypeDesc() + ", " + getSecondTypeDesc() + "]";
   }
}
