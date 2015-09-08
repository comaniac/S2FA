package com.amd.aparapi.internal.model;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;

import com.amd.aparapi.internal.model.HardCodedClassModel.TypeParameters;

public class HardCodedClassModels implements Iterable<HardCodedClassModel> {
	private final Map<String, List<HardCodedClassModel>> hardCodedClassModels =
	  new HashMap<String, List<HardCodedClassModel>>();

	public void addClassModelFor(Class<?> clz, HardCodedClassModel model) {
		if (!hardCodedClassModels.containsKey(clz.getName())) {
			hardCodedClassModels.put(clz.getName(),
			                         new LinkedList<HardCodedClassModel>());
		}
		hardCodedClassModels.get(clz.getName()).add(model);
	}

	public HardCodedClassModel getClassModelFor(String className,
	    HardCodedClassModelMatcher matcher) {
		if (hardCodedClassModels.containsKey(className)) {
			List<HardCodedClassModel> classModels = hardCodedClassModels.get(
			    className);
			// if (classModels.size() == 1) return classModels.get(0);

			for (HardCodedClassModel model : classModels) {
				if (matcher.matches(model))
					return model;
			}
		}
		return null;
	}

	@Override
	public Iterator<HardCodedClassModel> iterator() {
		List<HardCodedClassModel> accClassModels =
		  new LinkedList<HardCodedClassModel>();
		for (Map.Entry<String, List<HardCodedClassModel>> entry :
		     hardCodedClassModels.entrySet()) {
			for (HardCodedClassModel model : entry.getValue())
				accClassModels.add(model);
		}
		return accClassModels.iterator();
	}

	public abstract static class HardCodedClassModelMatcher {
		public abstract boolean matches(HardCodedClassModel model);
	}

	public static class DescMatcher extends HardCodedClassModelMatcher {
		private final String[] desc;

		public DescMatcher(String[] desc) {
			this.desc = desc;
		}

		@Override
		public boolean matches(HardCodedClassModel model) {
			TypeParameters paramDescs = model.getTypeParamDescs();
			if (paramDescs.size() == desc.length) {
				int index = 0;
				for (String d : paramDescs) {
					if (!d.equals(desc[index])) return false;
					index++;
				}
				return true;
			}
			return false;
		}

		@Override
		public String toString() {
			StringBuilder sb = new StringBuilder();
			sb.append("DescMatcher[");
			boolean first = true;
			for (String d : desc) {
				if (!first) sb.append(", ");
				sb.append(d);
				first = false;
			}
			sb.append("]");
			return sb.toString();
		}
	}

	public static class ShouldNotCallMatcher extends HardCodedClassModelMatcher {
		@Override
		public boolean matches(HardCodedClassModel model) {
			throw new RuntimeException("This matcher should only be used on " +
			                           "types which are not parameterized");
		}

		@Override
		public String toString() {
			return "ShouldNotCall";
		}
	}
}
