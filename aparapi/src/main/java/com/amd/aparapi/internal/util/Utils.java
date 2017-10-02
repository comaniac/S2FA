package com.amd.aparapi.internal.util;

import java.util.*;
import com.amd.aparapi.internal.model.MethodModel.METHODTYPE;
import com.amd.aparapi.internal.model.*;
import com.amd.aparapi.internal.writer.*;
import com.amd.aparapi.internal.writer.JParameter.DIRECTION;

/**
 * This utility class encapsulates the necessary actions required when processing and generating the kernel.
 */
public class Utils {

    public static String cleanClassName(String clazz) {
        String tname = clazz.replace(".", "/").replace(";", "").trim();
        if (tname.contains("$")) // Get rid of inner class names
            tname = tname.substring(0, tname.indexOf("$"));
        if (tname.startsWith("L"))
            tname = tname.substring(1);
        return tname;
    }

    public static boolean isPrimitive(String _type) {
        if (_type == null)
            return true;

        String type = _type.replace("[]", "").replace("[", "").trim();

        if (type.startsWith("I") || type.startsWith("F") ||
                type.startsWith("D") || type.startsWith("J") ||
                type.startsWith("B") || type.startsWith("C") ||
                type.startsWith("S") || type.startsWith("Z"))
            return true;
        else if (type.startsWith("int") || type.startsWith("float") ||
                 type.startsWith("double") || type.startsWith("long") ||
                 type.startsWith("byte") || type.startsWith("char") ||
                 type.startsWith("short") || type.startsWith("boolean"))
            return true;
        else
            return false;
    }

    public static String convertToCType(String type) {
        boolean isArray = false;
        String arrLength = "";
        String newType = "";
        String varName = "";

        // Constructor has no return type but we need "void" in C
        if (type == null)
            return "void";

        // Support variable declaration. i.e. I a -> int a
        if (type.contains(" ")) {
            varName = type.substring(type.indexOf(" "), type.length());
            type = type.substring(0, type.indexOf(" "));
        }

        // Internal used type representation
        // e.g. 128[I -> int[128]
        if (Character.isDigit(type.charAt(0))) {
            arrLength = type.substring(0, type.indexOf("["));
            type = type.substring(type.indexOf("["));
        }

        if (type.startsWith("[")) {
            isArray = true;
            type = type.substring(1);
        }

        if (type.equals("I") || type.equals("Z"))
            newType += "int";
        else if (type.equals("F"))
            newType += "float";
        else if (type.equals("D"))
            newType += "double";
        else if (type.equals("J"))
            newType += "long";
        else if (type.equals("S"))
            newType += "short";
        else if (type.equals("C") || type.equals("B"))
            newType += "char";
        else {
            if (type.startsWith("L"))
                newType += type.substring(1);
            else
                newType += type;
            newType = newType.replace(".", "")
                      .replace("/", "_").replace(";", "");
        }

        if (isArray) {
            if (arrLength.equals(""))
                newType += "*";
            else
                newType += "[" + arrLength + "]";
        }

        return newType + varName;
    }

    public static String convertToBytecodeType(String type) {
        return convertToBytecodeType(type, false);
    }

    public static String convertToBytecodeType(String type, boolean eraseGeneric) {
        if (Character.isDigit(type.charAt(0)))
            return type;
        else if (type.trim().endsWith("]")) {
            String arrLength = type.substring(type.indexOf("[") + 1, type.indexOf("]"));
            String baseType = type.trim().substring(0, type.indexOf("["));
            return arrLength + "[" + convertToBytecodeType(baseType, eraseGeneric);
        } else if (type.startsWith("["))
            return "[" + convertToBytecodeType(type.substring(1));
        else if (type.equals("int"))     return "I";
        else if (type.equals("float"))     return "F";
        else if (type.equals("double"))     return "D";
        else if (type.equals("long"))     return "J";
        else if (type.equals("short"))      return "S";
        else if (type.equals("char"))      return "C";
        else if (type.equals("boolean")) return "Z";
        else if (type.equals("byte"))      return "B";
        else if (type.equals("void"))    return "V";
        else if (type.equals("I") || type.equals("F") || type.equals("D") ||
                 type.equals("J") || type.equals("S") || type.equals("C") ||
                 type.equals("Z") || type.equals("B") || type.equals("V"))
            return type;
        else if (type.contains("<")) {
            String baseType = convertToBytecodeType(type.substring(0, type.indexOf("<")));
            if (eraseGeneric)
                return baseType;
            else {
                String fullType = baseType.replace(";", "") + "<";
                String genericTypes = type.substring(
                                          type.indexOf("<") + 1, type.lastIndexOf(">")).trim();
                boolean isFirst = true;
                for (String gType : genericTypes.split(",")) {
                    if (!isFirst)
                        fullType += ",";
                    fullType += convertToBytecodeType(gType, false);
                    isFirst = false;
                }
                return fullType + ">;";
            }
        } else if (!type.startsWith("L"))
            return "L" + type.replace(".", "/") + ";";
        return type.replace(".", "/");
    }
}
