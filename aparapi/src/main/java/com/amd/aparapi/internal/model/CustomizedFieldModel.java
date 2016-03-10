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

  public CustomizedFieldModel(String type, String name, int offset) {
    this.name = name;
    if (type.contains("[]")) {
      this.type = type.replace("[]", "").trim();
      this.ary = true;
    }
    else {
      this.type = type;
      this.ary = false;
    }
    this.offset = offset;

		boolean matchTypeSpec = false;
		shortType = Utils.mapShortType(this.type);
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

	// User APIs for customized method body

	public String genArrayElementAccess(String idx) {
		if (this.ary == false) {
			throw new RuntimeException("Access scalar field " + this.name + " with index, " + 
				"use genAccess() instead.");
		}
		return "this->" + this.name + "[" + idx + "]";
	}

  public String genAccess() {
    return "this->" + this.name;
  }
}
