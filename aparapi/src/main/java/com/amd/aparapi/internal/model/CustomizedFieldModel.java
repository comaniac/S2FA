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

  public CustomizedFieldModel(String name, String type, int offset) {
    this.name = name;
    if (type.contains("[]")) {
      this.type = Utils.convertToCType(type.replace("[]", "").trim());
			this.shortType = Utils.convertToBytecodeType(type.replace("[]", "").trim());
      this.ary = true;
    }
		else if (type.startsWith("[")) {
      this.type = Utils.convertToCType(type.replace("[", "").trim());
			this.shortType = Utils.convertToBytecodeType(type.replace("[", "").trim());
      this.ary = true;
		}
    else {
      this.type = Utils.convertToCType(type);
			this.shortType = Utils.convertToBytecodeType(type);
      this.ary = false;
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

	public boolean isArray() {
		return ary;
	}

	public String getDeclareCode() {
		String s = type + " ";
		if (isArray())
			s += "*";
		return s + name;
	}

	// User APIs for customized method body

	public String genArrayElementAccess(String idx) {
		if (this.ary == false) {
			throw new RuntimeException("Access scalar field " + this.name + " with index, " + 
				"use genAccess() instead.");
		}
		return "this->" + this.name + "[" + idx + "]";
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
