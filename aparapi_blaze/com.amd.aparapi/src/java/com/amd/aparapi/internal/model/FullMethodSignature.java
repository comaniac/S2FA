package com.amd.aparapi.internal.model;

import java.util.List;
import java.util.LinkedList;

/*
 * Does not support nested type parameters.
 */
public class FullMethodSignature {
    final TypeSignature returnType;
    final List<TypeSignature> paramTypes = new LinkedList<TypeSignature>();

    public FullMethodSignature(String sig) {
        int paramsStart = sig.indexOf('(');
        int paramsEnd = sig.indexOf(')');

        String params = sig.substring(paramsStart + 1, paramsEnd);
        int index = 0;
        int nesting = 0;
        int start = 0;
        while (index < params.length()) {
            if (params.charAt(index) == '<') {
                nesting++;
            } else if (params.charAt(index) == '>') {
                nesting--;
            } else if (params.charAt(index) == ';' && nesting == 0) {
                String paramType = params.substring(start, index + 1);
                this.paramTypes.add(new TypeSignature(paramType));
                start = index + 1;
            }
            index++;
        }

        this.returnType = new TypeSignature(sig.substring(paramsEnd + 1));
    }

    public TypeSignature getReturnType() {
        return returnType;
    }

    public List<TypeSignature> getTypeParameters() {
        return paramTypes;
    }

    public static class TypeSignature {
        private final String baseType;
        private final List<String> typeParameters = new LinkedList<String>();

        // Only parses object types
        public TypeSignature(String sig) {
            if (sig.indexOf('<') != -1) {
                int paramsStart = sig.indexOf('<');
                int paramsEnd = sig.indexOf('>');
                this.baseType = sig.substring(0, paramsStart) + ";";
                for (String t : sig.substring(paramsStart + 1, paramsEnd).split(";")) {
                    this.typeParameters.add(t + ";");
                }
            } else {
                baseType = sig;
            }
        }

        public static boolean compatible(String a, String b) {
            String typea = a.replace('.', '/');
            String typeb = b.replace('.', '/');

            if (typea.equals(typeb)) return true;

            if (typea.length() == 1 && typeb.equals("Ljava/lang/Object;")) {
                return true;
            } else if (typeb.length() == 1 && typea.equals("Ljava/lang/Object;")) {
                return true;
            }

            return false;
        }

        public String getBaseType() {
            return baseType;
        }

        public List<String> getTypeParameters() {
            return typeParameters;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(baseType);
            sb.append(" < ");
            for (String t : typeParameters) {
                sb.append(t);
                sb.append(" ");
            }
            sb.append(">");
            return sb.toString();
        }
    }
}
