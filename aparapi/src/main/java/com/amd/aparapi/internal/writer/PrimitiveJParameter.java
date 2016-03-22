package com.amd.aparapi.internal.writer;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import com.amd.aparapi.internal.model.ClassModel;
import com.amd.aparapi.internal.model.Entrypoint;

public class PrimitiveJParameter extends JParameter {

	public PrimitiveJParameter(String fullSig, String name, DIRECTION dir) {
		super(fullSig, name, dir);
	}

	@Override
	public String getParameterCode() {
		// Map/MapPartition arguments must be array
		// FIXME: Use 2-D array as long as Merlin compiler has supported it.
		if (isArray() || !isReference())
			return "__global " + getCType() + " *" + name;
		else
			return getCType() + " " + name;
	}

	@Override
	public void init(Entrypoint ep) {
		;
	}

	@Override
	public boolean isPrimitive() {
		return true;
	}
}
