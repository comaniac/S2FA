package com.amd.aparapi.internal.model;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import com.amd.aparapi.internal.model.CustomizedClassModel.TypeParameters;

public class CustomizedClassModels implements Iterable<CustomizedClassModel> {
	private final Map<String, List<CustomizedClassModel>> customizedClassModels =
	  new HashMap<String, List<CustomizedClassModel>>();

	public void addClass(CustomizedClassModel model) {
		if (!customizedClassModels.containsKey(model.getClassName())) {
			customizedClassModels.put(model.getClassName(),
			                         new LinkedList<CustomizedClassModel>());
		}
		if (model.hasTypeParams() || customizedClassModels.get(model.getClassName()).size() < 2)
			customizedClassModels.get(model.getClassName()).add(model);
	}

	public List<CustomizedClassModel> get(String className) {
		if (customizedClassModels.containsKey(className))
			return customizedClassModels.get(className);
		return null;	
	}

	public CustomizedClassModel getSample(String className) {
		if (customizedClassModels.containsKey(className)) {
			List<CustomizedClassModel> classModels = 
					customizedClassModels.get(className);
			return classModels.get(0);
		}
		return null;	
	}

	public CustomizedClassModel get(String className,
	    CustomizedClassModelMatcher matcher) {
		if (customizedClassModels.containsKey(className)) {
			List<CustomizedClassModel> classModels = customizedClassModels
				.get(className);

			for (CustomizedClassModel model : classModels) {
				if (matcher.matches(model))
					return model;
			}
		}
		return null;
	}

	public boolean hasClass(String name) {
		return customizedClassModels.containsKey(name);
	}

	public Set<String> getClassList() {
		return customizedClassModels.keySet();
	}

	@Override
	public Iterator<CustomizedClassModel> iterator() {
		@SuppressWarnings("unchecked")
		List<CustomizedClassModel> accClassModels =
		  new LinkedList<CustomizedClassModel>();

		for (Map.Entry<String, List<CustomizedClassModel>> entry :
		     customizedClassModels.entrySet()) {
			for (CustomizedClassModel model : entry.getValue())
				accClassModels.add(model);
		}
		return accClassModels.iterator();
	}

	public static class CustomizedClassModelMatcher {
		private final String[] params;

		public CustomizedClassModelMatcher(String[] typeParams) {
			this.params = typeParams;
		}

		public boolean matches(CustomizedClassModel model) {
			TypeParameters thatParams = model.getTypeParams();
			if (thatParams.size() == 0 && this.params == null)
				return true;
			if (thatParams.size() == this.params.length) {
				int index = 0;
				for (String d : thatParams) {
					if (!d.equals(this.params[index])) return false;
					index += 1;
				}
				return true;
			}
			return false;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("CustomizedClassModelMatcher[");
			boolean first = true;
			for (String d : this.params) {
				if (!first) sb.append(", ");
				sb.append(d);
				first = false;
			}
			sb.append("]");
			return sb.toString();
		}
	}
}
