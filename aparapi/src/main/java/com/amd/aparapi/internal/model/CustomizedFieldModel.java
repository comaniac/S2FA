package com.amd.aparapi.internal.model;

import java.util.*;
import com.amd.aparapi.internal.util.Utils;
import com.amd.aparapi.internal.exception.*;
import com.amd.aparapi.internal.instruction.InstructionSet.TypeSpec;

public class CustomizedFieldModel {
    // TODO: Support type parameters
    private final String name;
    private final String type;
    private final String shortType;
    private final TypeSpec typeSpec;
    private final int offset;
    private final boolean ary;
    private final int arrLength;

    public CustomizedFieldModel(String name, String type, int offset) {
        this.name = name;
        if (type.contains("]")) {
            String baseType = type.substring(0, type.indexOf("[")).trim();
            String lengthStr = type.substring(type.indexOf("[") + 1, type.indexOf("]"));
            this.type = Utils.convertToCType(baseType);
            this.shortType = Utils.convertToBytecodeType(baseType);
            this.ary = true;
            if (!lengthStr.equals(""))
                this.arrLength = Integer.parseInt(lengthStr);
            else
                this.arrLength = -1;
        } else if (type.contains("[")) {
            String baseType = type;
            if (Character.isDigit(type.charAt(0))) {
                baseType = type.substring(type.indexOf("[")).trim();
                this.arrLength = Integer.parseInt(type.substring(0, type.indexOf("[")));
            } else
                this.arrLength = -1;
            this.type = Utils.convertToCType(baseType);
            this.shortType = Utils.convertToBytecodeType(baseType);
            this.ary = true;
        } else {
            this.type = Utils.convertToCType(type);
            this.shortType = Utils.convertToBytecodeType(type);
            this.ary = false;
            this.arrLength = -1;
        }
        this.offset = offset;

        boolean matchTypeSpec = false;
        for (TypeSpec t : TypeSpec.values()) {
            if (t.getShortName().equals(shortType)) {
                matchTypeSpec = true;
                break;
            }
        }
        if (matchTypeSpec == false)
            this.typeSpec = TypeSpec.O;
        else
            this.typeSpec = TypeSpec.valueOf(shortType);
    }

    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public String getShortType() {
        return shortType;
    }

    public TypeSpec getTypeSpec() {
        return typeSpec;
    }

    public int getOffset() {
        return offset;
    }

    public boolean knowArrLength() {
        return this.arrLength != -1;
    }

    public boolean isArray() {
        return ary;
    }

    public String getDeclareCode() {
        String s = this.type + " ";
        if (this.ary) {
            if (this.arrLength == -1)
                return s + "*" + name;
            else
                return s + name + "[" + this.arrLength + "]";
        }
        return s + name;
    }

    // User APIs for customized method body

    public String genArrayElementAccess(String idx) {
        if (this.ary == false) {
            throw new RuntimeException("Access scalar field " + this.name + " with index, "
                                       +
                                       "use genAccess() instead.");
        }
        return "this->" + this.name + "[" + idx + "]";
    }

    public String genMemcpy(String cpyFrom) {
        assert(this.arrLength != -1);
        return "memcpy(this->" + this.name + ", " +
               cpyFrom + ", sizeof(" + this.type + ") * " +
               this.arrLength + ")";
    }

    public String genAssign(String assignFrom) {
        return "this->" + this.name + " = " + assignFrom;
    }

    public String genAccess() {
        if (Utils.isPrimitive(shortType) || isArray())
            return "this->" + this.name;
        else
            return "&this->" + this.name;
    }
}
