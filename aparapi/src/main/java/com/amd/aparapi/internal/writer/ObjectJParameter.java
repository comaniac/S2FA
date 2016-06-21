package com.amd.aparapi.internal.writer;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedList;
import java.util.Arrays;
import com.amd.aparapi.internal.model.Entrypoint;
import com.amd.aparapi.internal.model.ClassModel;
import com.amd.aparapi.internal.model.CustomizedClassModel;
import com.amd.aparapi.internal.model.CustomizedClassModel.TypeParameters;
import com.amd.aparapi.internal.model.CustomizedClassModels;
import com.amd.aparapi.internal.model.CustomizedFieldModel;

public class ObjectJParameter extends JParameter {

	public ObjectJParameter(String fullSig, String name, DIRECTION dir) {
		super(fullSig, name, dir);
	}

	public boolean hasTypeParameters() {
		if (typeParameters.size() != 0)
			return true;
		return false;
	}

	@Override
	public String getParameterCode() {
		String param = getCType() + " ";

		// Objects must pass by address
		param += "*" + name;
		return param;
	}

	@Override
	public void init(Entrypoint ep) {
		CustomizedClassModels models = ep.getCustomizedClassModels();
		if (models.hasClass(getTypeName())) {
			TypeParameters typeParams = new TypeParameters(Arrays.asList(getDescArray()));
			Class<?> clazz = models.getSample(getTypeName()).getClass();
			logger.fine("Initializing parameter " + this.toString() + " using " + clazz.toString());
			try {
				if (hasTypeParameters() == true) {
					clazzModel = (CustomizedClassModel) clazz
							.getConstructor(TypeParameters.class)
							.newInstance(typeParams);
				}
				else {
					clazzModel = (CustomizedClassModel) clazz
							.getConstructor()
							.newInstance();
				}
			} catch (Exception e) {
				throw new RuntimeException("Cannot construct customized class model");
			}
		}
	}

	@Override
	public boolean isPrimitive() {
		return false;
	}
}

