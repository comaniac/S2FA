/*
Copyright (c) 2010-2011, Advanced Micro Devices, Inc.
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the
following conditions are met:

Redistributions of source code must retain the above copyright notice, this list of conditions and the following
disclaimer.

Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
disclaimer in the documentation and/or other materials provided with the distribution.

Neither the name of the copyright holder nor the names of its contributors may be used to endorse or promote products
derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

If you use the software (in whole or in part), you shall adhere to all applicable U.S., European, and other export
laws, including but not limited to the U.S. Export Administration Regulations ("EAR"), (15 C.F.R. Sections 730 through
774), and E.U. Council Regulation (EC) No 1334/2000 of 22 June 2000.  Further, pursuant to Section 740.6 of the EAR,
you hereby certify that, except pursuant to a license granted by the United States Department of Commerce Bureau of
Industry and Security or as otherwise permitted pursuant to a License Exception under the U.S. Export Administration
Regulations ("EAR"), you will not (1) export, re-export or release to a national of a country in Country Groups D:1,
E:1 or E:2 any restricted technology, software, or source code you receive hereunder, or (2) export to Country Groups
D:1, E:1 or E:2 the direct product of such technology or software, if such foreign produced direct product is subject
to national security controls as identified on the Commerce Control List (currently found in Supplement 1 to Part 774
of EAR).  For the most current Country Group listings, or for additional information about the EAR or your obligations
under those regulations, please refer to the U.S. Bureau of Industry and Security's website at http://www.bis.doc.gov/.

*/
package com.amd.aparapi.internal.model;

import com.amd.aparapi.*;
import com.amd.aparapi.internal.exception.*;
import com.amd.aparapi.internal.instruction.*;
import com.amd.aparapi.internal.instruction.InstructionSet.*;
import com.amd.aparapi.internal.model.ClassModel.*;
import com.amd.aparapi.internal.model.ClassModel.ConstantPool.*;
import com.amd.aparapi.internal.model.ClassModel.ConstantPool.MethodReferenceEntry.*;
import com.amd.aparapi.internal.util.*;

import com.amd.aparapi.internal.writer.BlockWriter.ScalaParameter;
import com.amd.aparapi.internal.writer.BlockWriter.ScalaArrayParameter;
import com.amd.aparapi.internal.model.HardCodedClassModels.HardCodedClassModelMatcher;
import com.amd.aparapi.internal.model.HardCodedClassModels.DescMatcher;
import com.amd.aparapi.internal.model.HardCodedClassModels.ShouldNotCallMatcher;
import com.amd.aparapi.internal.model.HardCodedClassModel.TypeParameters;

import java.lang.reflect.*;
import java.util.*;
import java.util.logging.*;

public class Entrypoint implements Cloneable {

	private static Logger logger = Logger.getLogger(Config.getLoggerName());

	private final List<ClassModel.ClassModelField> referencedClassModelFields = new
	ArrayList<ClassModel.ClassModelField>();

	private final List<Field> referencedFields = new ArrayList<Field>();

	private ClassModel classModel;

	private final HardCodedClassModels hardCodedClassModels;

	public HardCodedClassModels getHardCodedClassModels() {
		return hardCodedClassModels;
	}

	private Object kernelInstance = null;

	private final boolean fallback = false;

	/*
	 * A mapping from referenced fields to hints to their actual types. Some
	 * fields may have their types obscured by Scala bytecode obfuscation (e.g.
	 * scala.runtime.ObjectRef) but we can take guesses at the actual type based
	 * on the references to them (e.g. if they are cast to something).
	 */
	private final Map<String, String> referencedFieldNames = new HashMap<String, String>();
	private void addToReferencedFieldNames(String name, String hint) {
		if (referencedFieldNames.containsKey(name)) {
			if (hint != null)
				referencedFieldNames.put(name, hint);
		} else
			referencedFieldNames.put(name, hint);
	}

	private final Set<String> arrayFieldAssignments = new LinkedHashSet<String>();

	private final Set<String> arrayFieldAccesses = new LinkedHashSet<String>();

	// Classes of object array members
	private final StringToModel objectArrayFieldsClasses = new StringToModel();

	private final List<String> lexicalOrdering = new LinkedList<String>();

	private void addToObjectArrayFieldsClasses(String name, ClassModel model) {
		objectArrayFieldsClasses.add(name, model);
	}

	public ClassModel getModelFromObjectArrayFieldsClasses(String name,
	    ClassModelMatcher matcher) {
		return objectArrayFieldsClasses.get(name, matcher);
	}

	public List<ClassModel> getModelsForClassName(String name) {
		return objectArrayFieldsClasses.getModels(name);
	}

	public Iterator<ClassModel> getObjectArrayFieldsClassesIterator() {
		return objectArrayFieldsClasses.iterator();
	}

	private void addClass(String name, String[] desc) throws AparapiException {
		final ClassModel model = getOrUpdateAllClassAccesses(name,
		                         new DescMatcher(desc));
		addToObjectArrayFieldsClasses(name, model);

		lexicalOrdering.add(name);
		allFieldsClasses.add(name, model);
	}

	// Supporting classes of object array members like supers
	private final StringToModel allFieldsClasses = new StringToModel();

	// Keep track of arrays whose length is taken via foo.length
	private final Set<String> arrayFieldArrayLengthUsed = new LinkedHashSet<String>();

	private final List<MethodModel> calledMethods = new ArrayList<MethodModel>();

	private final MethodModel methodModel;

	/**
	   True is an indication to use the fp64 pragma
	*/
	private boolean usesDoubles;

	private boolean usesNew;

	/**
	   True is an indication to use the byte addressable store pragma
	*/
	private boolean usesByteWrites;

	/**
	   True is an indication to use the atomics pragmas
	*/
	private boolean usesAtomic32;

	private boolean usesAtomic64;

	public boolean requiresDoublePragma() {
		return usesDoubles;
	}

	public boolean requiresHeap() {
		return usesNew;
	}

	public boolean requiresByteAddressableStorePragma() {
		return usesByteWrites;
	}

	/* Atomics are detected in Entrypoint */
	public void setRequiresAtomics32Pragma(boolean newVal) {
		usesAtomic32 = newVal;
	}

	public void setRequiresAtomics64Pragma(boolean newVal) {
		usesAtomic64 = newVal;
	}

	public boolean requiresAtomic32Pragma() {
		return usesAtomic32;
	}

	public boolean requiresAtomic64Pragma() {
		return usesAtomic64;
	}

	public Object getKernelInstance() {
		return kernelInstance;
	}

	public void setKernelInstance(Object _k) {
		kernelInstance = _k;
	}

	public List<String> getLexicalOrderingOfObjectClasses() {
		return lexicalOrdering;
	}

	public static Field getFieldFromClassHierarchy(Class<?> _clazz,
	    String _name) throws AparapiException {

		// look in self
		// if found, done

		// get superclass of curr class
		// while not found
		//  get its fields
		//  if found
		//   if not private, done
		//  if private, failure
		//  if not found, get next superclass

		Field field = null;

		assert _name != null : "_name should not be null";

		if (logger.isLoggable(Level.FINE))
			logger.fine("looking for " + _name + " in " + _clazz.getName());

		try {
			field = _clazz.getDeclaredField(_name);
			final Class<?> type = field.getType();
			if (field.getAnnotation(Kernel.NoCL.class) != null)
				return null;
			return field;
		} catch (final NoSuchFieldException nsfe) {
			// This should be looger fine...
			//System.out.println("no " + _name + " in " + _clazz.getName());
		}

		Class<?> mySuper = _clazz.getSuperclass();

		if (logger.isLoggable(Level.FINE))
			logger.fine("looking for " + _name + " in " + mySuper.getName());

		// Find better way to do this check
		while (mySuper != null && !mySuper.getName().equals(Kernel.class.getName())) {
			try {
				field = mySuper.getDeclaredField(_name);
				final int modifiers = field.getModifiers();
				if ((Modifier.isStatic(modifiers) == false) && (Modifier.isPrivate(modifiers) == false)) {
					final Class<?> type = field.getType();
					if (logger.isLoggable(Level.FINE))
						logger.fine("field type is " + type.getName());
					if (type.isPrimitive() || type.isArray())
						return field;
					throw new ClassParseException(ClassParseException.TYPE.OBJECTFIELDREFERENCE);
				} else {
					// This should be looger fine...
					//System.out.println("field " + _name + " not suitable: " + java.lang.reflect.Modifier.toString(modifiers));
					return null;
				}
			} catch (final NoSuchFieldException nsfe) {
				if (logger.isLoggable(Level.FINE))
					logger.fine("no " + _name + " in " + mySuper.getName());
				mySuper = mySuper.getSuperclass();
			}
		}
		return null;
	}

	/*
	 * Update the list of object array member classes and all the superclasses
	 * of those classes and the fields in each class
	 *
	 * It is important to have only one ClassModel for each class used in the kernel
	 * and only one MethodModel per method, so comparison operations work properly.
	 */
	public ClassModel getOrUpdateAllClassAccesses(String className,
	    HardCodedClassModelMatcher matcher) throws AparapiException {
		ClassModel memberClassModel = allFieldsClasses.get(className, ClassModel.wrap(className, matcher));
		if (memberClassModel == null) {
			try {
				final Class<?> memberClass = Class.forName(className);

				// Immediately add this class and all its supers if necessary
				memberClassModel = ClassModel.createClassModel(memberClass, this,
				                   matcher);

				if (logger.isLoggable(Level.FINEST))
					logger.finest("adding class " + className);
				allFieldsClasses.add(className, memberClassModel);
				ClassModel superModel = memberClassModel.getSuperClazz();
				while (superModel != null) {
					// See if super is already added
					final ClassModel oldSuper = allFieldsClasses.get(
					                              superModel.getClassWeAreModelling().getName(),
					                              new NameMatcher(superModel.getClassWeAreModelling().getName()));
					if (oldSuper != null) {
						if (oldSuper != superModel) {
							memberClassModel.replaceSuperClazz(oldSuper);
							if (logger.isLoggable(Level.FINEST))
								logger.finest("replaced super " + oldSuper.getClassWeAreModelling().getName() + " for " +
								              className);
						}
					} else {
						allFieldsClasses.add(superModel.getClassWeAreModelling().getName(), superModel);
						if (logger.isLoggable(Level.FINEST))
							logger.finest("add new super " + superModel.getClassWeAreModelling().getName() + " for " +
							              className);
					}

					superModel = superModel.getSuperClazz();
				}
			} catch (final Exception e) {
				if (logger.isLoggable(Level.INFO))
					logger.info("Cannot find: " + className);
				throw new AparapiException(e);
			}
		}

		return memberClassModel;
	}

	public ClassModelMethod resolveAccessorCandidate(final MethodCall _methodCall,
	    final MethodEntry _methodEntry) throws AparapiException {
		final String methodsActualClassName =
		  (_methodEntry.getClassEntry().getNameUTF8Entry().getUTF8()).replace('/', '.');

		if (_methodCall instanceof VirtualMethodCall) {
			final Instruction callInstance = ((VirtualMethodCall) _methodCall).getInstanceReference();
			if (callInstance instanceof AccessArrayElement) {
				final AccessArrayElement arrayAccess = (AccessArrayElement) callInstance;
				final Instruction refAccess = arrayAccess.getArrayRef();
				//if (refAccess instanceof I_GETFIELD) {

				// It is a call from a member obj array element
				if (logger.isLoggable(Level.FINE))
					logger.fine("Looking for class in accessor call: " + methodsActualClassName);

				final String methodName =
				  _methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
				final String methodDesc =
				  _methodEntry.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8();
				final String returnType = methodDesc.substring(methodDesc.lastIndexOf(')') + 1);
				HardCodedClassModelMatcher matcher = new HardCodedClassModelMatcher() {
					@Override
					public boolean matches(HardCodedClassModel model) {
						// TODO use _methodCall and _methodEntry?
						TypeParameters params = model.getTypeParamDescs();
						if (methodName.startsWith("_1")) {
							String first = params.get(0);
							if (returnType.length() == 1) {
								// Primitive
								return returnType.equals(first);
							} else if (returnType.startsWith("L")) {
								// Object
								return first.startsWith("L"); // #*&$% type erasure
							} else
								throw new RuntimeException(returnType);
						} else if (methodName.startsWith("_2")) {
							String second = params.get(1);
							if (returnType.length() == 1) {
								// Primitive
								return returnType.equals(second);
							} else if (returnType.startsWith("L")) {
								// Object
								return second.startsWith("L"); // #*&$% type erasure
							} else
								throw new RuntimeException(returnType);
						}
						return false;
					}
				};

				final ClassModel memberClassModel = getOrUpdateAllClassAccesses(methodsActualClassName, matcher);

				// false = no invokespecial allowed here
				return memberClassModel.getMethod(_methodEntry, false);
				//}
			}
		}
		return null;
	}

	/*
	 * Update accessor structures when there is a direct access to an
	 * obect array element's data members
	 */
	public void updateObjectMemberFieldAccesses(final String className,
	    final FieldEntry field) throws AparapiException {
		final String accessedFieldName = field.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();

		// Quickly bail if it is a ref
		if (field.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8().startsWith("L")
		    || field.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8().startsWith("[L")) {
			System.err.println("Referencing field " + accessedFieldName + " in " + className);
			throw new ClassParseException(ClassParseException.TYPE.OBJECTARRAYFIELDREFERENCE);
		}

		if (logger.isLoggable(Level.FINEST))
			logger.finest("Updating access: " + className + " field:" + accessedFieldName);

		HardCodedClassModelMatcher matcher = new HardCodedClassModelMatcher () {
			@Override
			public boolean matches(HardCodedClassModel model) {
				// TODO can we use the type of field to infer the right Tuple2? Maybe we need to have per-type HardCodedClassModel matches?

				throw new UnsupportedOperationException();
			}
		};

		final ClassModel memberClassModel = getOrUpdateAllClassAccesses(className, matcher);
		final Class<?> memberClass = memberClassModel.getClassWeAreModelling();
		ClassModel superCandidate = null;

		// We may add this field if no superclass match
		boolean add = true;

		// No exact match, look for a superclass
		for (final ClassModel c : allFieldsClasses) {
			if (logger.isLoggable(Level.FINEST))
				logger.finest(" super: " + c.getClassWeAreModelling().getName() + " for " + className);
			if (c.isSuperClass(memberClass)) {
				if (logger.isLoggable(Level.FINE))
					logger.fine("selected super: " + c.getClassWeAreModelling().getName() + " for " + className);
				superCandidate = c;
				break;
			}

			if (logger.isLoggable(Level.FINEST))
				logger.finest(" no super match for " + memberClass.getName());
		}

		// Look at super's fields for a match
		if (superCandidate != null) {
			final ArrayList<FieldNameInfo> structMemberSet = superCandidate.getStructMembers();
			for (final FieldNameInfo f : structMemberSet) {
				if (f.name.equals(accessedFieldName) &&
				    f.desc.equals(field.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8())) {

					if (logger.isLoggable(Level.FINE)) {
						logger.fine("Found match: " + accessedFieldName + " class: " +
						            field.getClassEntry().getNameUTF8Entry().getUTF8()
						            + " to class: " + f.className);
					}

					if (!f.className.equals(field.getClassEntry().getNameUTF8Entry().getUTF8())) {
						// Look up in class hierarchy to ensure it is the same field
						final Field superField = getFieldFromClassHierarchy(superCandidate.getClassWeAreModelling(),
						                         f.name);
						final Field classField = getFieldFromClassHierarchy(memberClass, f.name);
						if (!superField.equals(classField))
							throw new ClassParseException(ClassParseException.TYPE.OVERRIDENFIELD);
					}

					add = false;
					break;
				}
			}
		}

		// There was no matching field in the supers, add it to the memberClassModel
		// if not already there
		if (add) {
			boolean found = false;
			final ArrayList<FieldNameInfo> structMemberSet = memberClassModel.getStructMembers();
			for (final FieldNameInfo f : structMemberSet) {
				if (f.name.equals(accessedFieldName) &&
				    f.desc.equals(field.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8()))
					found = true;
			}
			if (!found) {
				FieldNameInfo fieldInfo = new FieldNameInfo(
				  field.getNameAndTypeEntry().getNameUTF8Entry().getUTF8(),
				  field.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8(),
				  field.getClassEntry().getNameUTF8Entry().getUTF8());
				structMemberSet.add(fieldInfo);
				if (logger.isLoggable(Level.FINE)) {
					logger.fine("Adding assigned field " + field.getNameAndTypeEntry().getNameUTF8Entry().getUTF8() +
					            " type: "
					            + field.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8() + " to "
					            + memberClassModel.getClassWeAreModelling().getName());
				}
			}
		}
	}

	/*
	 * Find a suitable call target in the kernel class, supers, object members or static calls
	 */
	ClassModelMethod resolveCalledMethod(final MethodCall methodCall,
	                                     ClassModel classModel) throws AparapiException {
		final MethodEntry methodEntry = methodCall.getConstantPoolMethodEntry();

		int thisClassIndex = classModel.getThisClassConstantPoolIndex();//arf
		boolean isMapped = (thisClassIndex != methodEntry.getClassIndex()) &&
		                   Kernel.isMappedMethod(methodEntry);
		if (logger.isLoggable(Level.FINE)) {
			if (methodCall instanceof I_INVOKESPECIAL)
				logger.fine("Method call to super: " + methodEntry);
			else if (thisClassIndex != methodEntry.getClassIndex())
				logger.fine("Method call to ??: " + methodEntry + ", isMappedMethod=" + isMapped);
			else
				logger.fine("Method call in kernel class: " + methodEntry);
		}

		ClassModelMethod m = classModel.getMethod(methodEntry,
		                     (methodCall instanceof I_INVOKESPECIAL) ? true : false);

		// Did not find method in this class or supers. Look for data member object arrays
		if (m == null && !isMapped)
			m = resolveAccessorCandidate(methodCall, methodEntry);

		// Look for a intra-object call in a object member
		if (m == null && !isMapped) {
			String targetMethodOwner = methodEntry.getClassEntry().getNameUTF8Entry().getUTF8().replace('/',
			                           '.');
			final Set<ClassModel> possibleMatches = new HashSet<ClassModel>();

			for (ClassModel c : allFieldsClasses) {
				if (c.getClassWeAreModelling().getName().equals(targetMethodOwner))
					m = c.getMethod(methodEntry, (methodCall instanceof I_INVOKESPECIAL) ? true : false);
				else if (c.classNameMatches(targetMethodOwner))
					possibleMatches.add(c);
			}

			if (m == null) {
				for (ClassModel c : possibleMatches) {
					m = c.getMethod(methodEntry,
					                (methodCall instanceof I_INVOKESPECIAL) ? true : false);
					if (m != null) break;
				}
			}
		}


		Set<String> ignorableClasses = new HashSet<String>();
		ignorableClasses.add("scala/runtime/BoxesRunTime");
		ignorableClasses.add("scala/runtime/IntRef");
		ignorableClasses.add("scala/runtime/FloatRef");
		ignorableClasses.add("scala/runtime/DoubleRef");
		// Look for static call to some other class
		if ((m == null) && !isMapped && (methodCall instanceof I_INVOKESTATIC) &&
		    !ignorableClasses.contains(methodEntry.getClassEntry().getNameUTF8Entry().getUTF8())) {

			String otherClassName =
			  methodEntry.getClassEntry().getNameUTF8Entry().getUTF8().replace('/', '.');
			HardCodedClassModelMatcher matcher = new HardCodedClassModelMatcher() {
				@Override
				public boolean matches(HardCodedClassModel model) {
					// TODO use _methodCall and _methodEntry?
					throw new UnsupportedOperationException();
				}
			};
			ClassModel otherClassModel = getOrUpdateAllClassAccesses(otherClassName, matcher);

			//if (logger.isLoggable(Level.FINE)) {
			//   logger.fine("Looking for: " + methodEntry + " in other class " + otherClass.getName());
			//}
			// false because INVOKESPECIAL not allowed here
			m = otherClassModel.getMethod(methodEntry, false);
		}

		if (logger.isLoggable(Level.INFO))
			logger.fine("Selected method for: " + methodEntry + " is " + m);

		return m;
	}

	public Entrypoint(ClassModel _classModel, MethodModel _methodModel,
	                  Object _k, Collection<ScalaArrayParameter> params, HardCodedClassModels setHardCodedClassModels)
	throws AparapiException {
		classModel = _classModel;
		methodModel = _methodModel;
		kernelInstance = _k;
		if (setHardCodedClassModels == null)
			hardCodedClassModels = new HardCodedClassModels();
		else
			hardCodedClassModels = setHardCodedClassModels;

		for (HardCodedClassModel model : hardCodedClassModels) {
			for (String desc : model.getNestedTypeDescs()) {
				// Convert object desc to class name
				String nestedClass = desc.substring(1, desc.length() - 1);
				lexicalOrdering.add(nestedClass);
				addToObjectArrayFieldsClasses(nestedClass,
				                              getOrUpdateAllClassAccesses(nestedClass,
				                                  new ShouldNotCallMatcher()));
			}
		}

//	comaniac: Now we don't want to model Tuple2 class.
//	Intead, we want to transform it as 2 variables and access them directly.
		if (params != null) {
			for (ScalaArrayParameter p : params) {
				if (p.getClazz() != null)
					addClass(p.getClazz().getName(), p.getDescArray());
			}
		}

		final Map<ClassModelMethod, MethodModel> methodMap = new
		LinkedHashMap<ClassModelMethod, MethodModel>();

		boolean discovered = true;

		// Record which pragmas we need to enable
		if (methodModel.requiresDoublePragma()) {
			usesDoubles = true;
			if (logger.isLoggable(Level.FINE))
				logger.fine("Enabling doubles on " + methodModel.getName());
		}
		if (methodModel.requiresHeap())
			usesNew = true;
		if (methodModel.requiresByteAddressableStorePragma()) {
			usesByteWrites = true;
			if (logger.isLoggable(Level.FINE))
				logger.fine("Enabling byte addressable on " + methodModel.getName());
		}

		// Collect all methods called directly from kernel's run method
		for (final MethodCall methodCall : methodModel.getMethodCalls()) {
			ClassModelMethod m = resolveCalledMethod(methodCall, classModel);
			if ((m != null) && !methodMap.keySet().contains(m) && !noCL(m) && !transformedMethod(m)) {
				final MethodModel target = new LoadedMethodModel(m, this);
				methodMap.put(m, target);
				methodModel.getCalledMethods().add(target);
				discovered = true;
			}
		}

		// methodMap now contains a list of method called by run itself().
		// Walk the whole graph of called methods and add them to the methodMap
		while (!fallback && discovered) {
			discovered = false;
			for (final MethodModel mm : new ArrayList<MethodModel>(methodMap.values())) {
				for (final MethodCall methodCall : mm.getMethodCalls()) {

					ClassModelMethod m = resolveCalledMethod(methodCall, classModel);
					if (m != null && !noCL(m)) {
						MethodModel target = null;
						if (methodMap.keySet().contains(m)) {
							// we remove and then add again.  Because this is a LinkedHashMap this
							// places this at the end of the list underlying the map
							// then when we reverse the collection (below) we get the method
							// declarations in the correct order.  We are trying to avoid creating forward references
							target = methodMap.remove(m);
							if (logger.isLoggable(Level.FINEST)) {
								logger.fine("repositioning : " + m.getClassModel().getClassWeAreModelling().getName() + " " +
								            m.getName()
								            + " " + m.getDescriptor());
							}
						} else {
							target = new LoadedMethodModel(m, this);
							discovered = true;
						}
						methodMap.put(m, target);
						// Build graph of call targets to look for recursion
						mm.getCalledMethods().add(target);
					}
				}
			}
		}

		methodModel.checkForRecursion(new HashSet<MethodModel>());

		if (logger.isLoggable(Level.FINE))
			logger.fine("fallback=" + fallback);

		if (!fallback) {
			calledMethods.addAll(methodMap.values());
			Collections.reverse(calledMethods);
			final List<MethodModel> methods = new ArrayList<MethodModel>(calledMethods);

			// add method to the calledMethods so we can include in this list
			methods.add(methodModel);
			final Set<String> fieldAssignments = new HashSet<String>();

			final Set<String> fieldAccesses = new HashSet<String>();

			// This is just a prepass that collects metadata, we don't actually write kernels at this point
			for (final MethodModel methodModel : methods) {

				// Record which pragmas we need to enable
				if (methodModel.requiresDoublePragma()) {
					usesDoubles = true;
					if (logger.isLoggable(Level.FINE))
						logger.fine("Enabling doubles on " + methodModel.getName());
				}
				if (methodModel.requiresHeap())
					usesNew = true;
				if (methodModel.requiresByteAddressableStorePragma()) {
					usesByteWrites = true;
					if (logger.isLoggable(Level.FINE))
						logger.fine("Enabling byte addressable on " + methodModel.getName());
				}

				for (Instruction instruction = methodModel.getPCHead(); instruction != null; instruction =
				       instruction.getNextPC()) {
					if (instruction instanceof AssignToArrayElement) {
						final AssignToArrayElement assignment = (AssignToArrayElement) instruction;

						final Instruction arrayRef = assignment.getArrayRef();
						// AccessField here allows instance and static array refs
						if (arrayRef instanceof I_GETFIELD) {
							final I_GETFIELD getField = (I_GETFIELD) arrayRef;
							final FieldEntry field = getField.getConstantPoolFieldEntry();
							final String assignedArrayFieldName = field.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
							arrayFieldAssignments.add(assignedArrayFieldName);
							addToReferencedFieldNames(assignedArrayFieldName, null);
							arrayFieldArrayLengthUsed.add(assignedArrayFieldName);

						}
					} else if (instruction instanceof AccessArrayElement) {
						final AccessArrayElement access = (AccessArrayElement) instruction;

						final Instruction arrayRef = access.getArrayRef();
						// AccessField here allows instance and static array refs
						if (arrayRef instanceof I_GETFIELD) {
							final I_GETFIELD getField = (I_GETFIELD) arrayRef;
							final FieldEntry field = getField.getConstantPoolFieldEntry();
							final String accessedArrayFieldName = field.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
							arrayFieldAccesses.add(accessedArrayFieldName);
							addToReferencedFieldNames(accessedArrayFieldName, null);
							arrayFieldArrayLengthUsed.add(accessedArrayFieldName);
						}
					} else if (instruction instanceof I_ARRAYLENGTH) {
						Instruction child = instruction.getFirstChild();

						// Issue #33: We may have an invokevirtual before getField.
						while (child instanceof I_AALOAD || child instanceof I_INVOKEVIRTUAL ||
						       child instanceof I_CHECKCAST)
							child = child.getFirstChild();

						if (!(child instanceof AccessField)) {
							System.out.println("Illegal child: " + child.toString());
							throw new ClassParseException(ClassParseException.TYPE.LOCALARRAYLENGTHACCESS);
						}

						final AccessField childField = (AccessField) child;
						String arrayName = childField.getConstantPoolFieldEntry()
						                   .getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
						arrayFieldArrayLengthUsed.add(arrayName);
						if (logger.isLoggable(Level.FINE))
							logger.fine("Noted arraylength in " + methodModel.getName() + " on " + arrayName);
					} else if (instruction instanceof AccessField) {
						final AccessField access = (AccessField) instruction;
						final FieldEntry field = access.getConstantPoolFieldEntry();
						final String accessedFieldName = field.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
						fieldAccesses.add(accessedFieldName);

						final String signature;
						if (access instanceof ScalaGetObjectRefField) {
							ScalaGetObjectRefField scalaGet = (ScalaGetObjectRefField)access;
							I_CHECKCAST cast = scalaGet.getCast();
							signature = cast.getConstantPoolClassEntry().getNameUTF8Entry().getUTF8().replace('.', '/');
							addToReferencedFieldNames(accessedFieldName, "L" + signature);
						} else {
							signature = field.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8();

							// Issue #34: Find type hint of broadcast fields.
							if (signature.contains("BlazeBroadcast")) {
								Instruction parent = instruction.getParentExpr();
								String typeHint = null;

								while (parent instanceof I_INVOKEVIRTUAL)
									parent = parent.getParentExpr();
								if (parent instanceof I_CHECKCAST) { // Array type
									I_CHECKCAST cast = (I_CHECKCAST) parent;
									typeHint = cast.getConstantPoolClassEntry().getNameUTF8Entry().getUTF8();
								} else if (parent instanceof I_INVOKESTATIC) { // Scalar type (boxed)
									I_INVOKESTATIC unbox = (I_INVOKESTATIC) parent;
									typeHint = unbox.getConstantPoolMethodEntry().getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
									if (typeHint.contains("Int"))
										typeHint = "I";
									else if (typeHint.contains("Double"))
										typeHint = "D";
									else if (typeHint.contains("Float"))
										typeHint = "F";
									else if (typeHint.contains("Long"))
										typeHint = "J";
									else
										typeHint = "null";
								}
								addToReferencedFieldNames(accessedFieldName, typeHint);
							} else
								addToReferencedFieldNames(accessedFieldName, null);
						}
						if (logger.isLoggable(Level.FINE))
							logger.fine("AccessField field type= " + signature + " in " + methodModel.getName());

						// Add the class model for the referenced obj array
						if (signature.startsWith("[L")) {
							// Turn [Lcom/amd/javalabs/opencl/demo/DummyOOA; into com.amd.javalabs.opencl.demo.DummyOOA for example
							final String className = (signature.substring(2, signature.length() - 1)).replace('/', '.');
							HardCodedClassModelMatcher matcher = new HardCodedClassModelMatcher() {
								@Override
								public boolean matches(HardCodedClassModel model) {
									return className.equals(model.getClassWeAreModelling().getName());
								}
							};

							final ClassModel arrayFieldModel = getOrUpdateAllClassAccesses(className, matcher);
							if (arrayFieldModel != null) {
								if (arrayFieldModel instanceof HardCodedClassModel) {
									addToReferencedFieldNames(accessedFieldName,
									                          "[" + ((HardCodedClassModel)arrayFieldModel).getDescriptor());
								}
								final Class<?> memberClass = arrayFieldModel.getClassWeAreModelling();
								final int modifiers = memberClass.getModifiers();
								// if (!Modifier.isFinal(modifiers)) {
								//    throw new ClassParseException(ClassParseException.TYPE.ACCESSEDOBJECTNONFINAL);
								// }

								final ClassModel refModel = getModelFromObjectArrayFieldsClasses(className,
								new ClassModelMatcher() {
									@Override
									public boolean matches(ClassModel model) {
										return className.equals(model.getClassWeAreModelling().getName());
									}
								});
								if (refModel == null) {

									// Verify no other member with common parent
									for (final ClassModel memberObjClass : objectArrayFieldsClasses) {
										ClassModel superModel = memberObjClass;
										while (superModel != null) {
											if (superModel.isSuperClass(memberClass))
												throw new ClassParseException(ClassParseException.TYPE.ACCESSEDOBJECTFIELDNAMECONFLICT);
											superModel = superModel.getSuperClazz();
										}
									}

									addToObjectArrayFieldsClasses(className, arrayFieldModel);
									if (logger.isLoggable(Level.FINE))
										logger.fine("adding class to objectArrayFields: " + className);
								}
							}
							lexicalOrdering.add(className);
						} 
						else {
							final String className = (field.getClassEntry().getNameUTF8Entry().getUTF8()).replace('/', '.');
							final Set<String> ignoreScalaRuntimeStuff = new HashSet<String>();
							ignoreScalaRuntimeStuff.add("scala.runtime.RichInt$");
							ignoreScalaRuntimeStuff.add("scala.Predef$");
							ignoreScalaRuntimeStuff.add("scala.ObjectRef");
							ignoreScalaRuntimeStuff.add("scala.runtime.ObjectRef");
							ignoreScalaRuntimeStuff.add("scala.runtime.IntRef");
							ignoreScalaRuntimeStuff.add("scala.math.package$");

							if (!ignoreScalaRuntimeStuff.contains(className)) {
								// Look for object data member access
								if (!className.equals(getClassModel().getClassWeAreModelling().getName())
								    && (getFieldFromClassHierarchy(getClassModel().getClassWeAreModelling(),
								                                   accessedFieldName) == null))
									updateObjectMemberFieldAccesses(className, field);
							}
						}
					} else if (instruction instanceof AssignToField) {
						final AssignToField assignment = (AssignToField) instruction;
						final FieldEntry field = assignment.getConstantPoolFieldEntry();
						final String assignedFieldName = field.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
						fieldAssignments.add(assignedFieldName);
						addToReferencedFieldNames(assignedFieldName, null);

						final String className = (field.getClassEntry().getNameUTF8Entry().getUTF8()).replace('/', '.');
						// Look for object data member access
						if (!className.equals(getClassModel().getClassWeAreModelling().getName())
						    && (getFieldFromClassHierarchy(getClassModel().getClassWeAreModelling(),
						                                   assignedFieldName) == null))
							updateObjectMemberFieldAccesses(className, field);
						else {

							if ((!Config.enablePUTFIELD) && methodModel.methodUsesPutfield() && !methodModel.isSetter())
								throw new ClassParseException(ClassParseException.TYPE.ACCESSEDOBJECTONLYSUPPORTSSIMPLEPUTFIELD);

						}

					} else if (instruction instanceof I_INVOKEVIRTUAL) {
						final I_INVOKEVIRTUAL invokeInstruction = (I_INVOKEVIRTUAL) instruction;
						MethodModel invokedMethod = invokeInstruction.getMethod();
						FieldEntry getterField = getSimpleGetterField(invokedMethod);
						if (getterField != null)
							addToReferencedFieldNames(getterField.getNameAndTypeEntry().getNameUTF8Entry().getUTF8(), null);
						else {
							final MethodEntry methodEntry = invokeInstruction.getConstantPoolMethodEntry();
							if (Kernel.isMappedMethod(methodEntry)) { //only do this for intrinsics

								if (Kernel.usesAtomic32(methodEntry))
									setRequiresAtomics32Pragma(true);

								final Arg methodArgs[] = methodEntry.getArgs();
								if ((methodArgs.length > 0) && methodArgs[0].isArray()) { //currently array arg can only take slot 0
									final Instruction arrInstruction = invokeInstruction.getArg(0);
									if (arrInstruction instanceof AccessField) {
										final AccessField access = (AccessField) arrInstruction;
										final FieldEntry field = access.getConstantPoolFieldEntry();
										final String accessedFieldName = field.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
										arrayFieldAssignments.add(accessedFieldName);
										addToReferencedFieldNames(accessedFieldName, null);
									} else
										throw new ClassParseException(ClassParseException.TYPE.ACCESSEDOBJECTSETTERARRAY);
								}
							}

						}
					} else if (instruction instanceof I_NEWARRAY) {
						Instruction child = instruction.getFirstChild();
						if (!(child instanceof BytecodeEncodedConstant) && // i_const
						    !(child instanceof ImmediateConstant)) // push
							throw new ClassParseException(ClassParseException.TYPE.NEWDYNAMICARRAY);
					}
				}
			}

			for (final Map.Entry<String, String> referencedField : referencedFieldNames.entrySet()) {
				String referencedFieldName = referencedField.getKey();
				String typeHint = referencedField.getValue(); // may be null

				try {
					final Class<?> clazz = classModel.getClassWeAreModelling();
					final Field field = getFieldFromClassHierarchy(clazz, referencedFieldName);
					if (field != null) {
						referencedFields.add(field);
						final ClassModelField ff = classModel.getField(referencedFieldName);
						assert ff != null : "ff should not be null for " + clazz.getName() + "." + referencedFieldName;
						if (typeHint != null) ff.setTypeHint(typeHint);
						referencedClassModelFields.add(ff);
					}
				} catch (final SecurityException e) {
					e.printStackTrace();
				}
			}

			// Build data needed for oop form transforms if necessary
			if (!objectArrayFieldsClasses.isEmpty()) {

				for (final ClassModel memberObjClass : objectArrayFieldsClasses) {
					// At this point we have already done the field override safety check, so
					// add all the superclass fields into the kernel member class to be
					// sorted by size and emitted into the struct
					ClassModel superModel = memberObjClass.getSuperClazz();
					while (superModel != null) {
						if (logger.isLoggable(Level.FINEST)) {
							logger.finest("adding = " + superModel.getClassWeAreModelling().getName() + " fields into "
							              + memberObjClass.getClassWeAreModelling().getName());
						}
						memberObjClass.getStructMembers().addAll(superModel.getStructMembers());
						superModel = superModel.getSuperClazz();
					}
				}

				// Sort fields of each class biggest->smallest
				final Comparator<FieldNameInfo> fieldSizeComparator = new Comparator<FieldNameInfo>() {
					@Override public int compare(FieldNameInfo aa, FieldNameInfo bb) {
						final String aType = aa.desc;
						final String bType = bb.desc;

						// Booleans get converted down to bytes
						final int aSize = getSizeOf(aType);
						final int bSize = getSizeOf(bType);

						if (logger.isLoggable(Level.FINEST))
							logger.finest("aType= " + aType + " aSize= " + aSize + " . . bType= " + bType + " bSize= " + bSize);

						// Note this is sorting in reverse order so the biggest is first
						if (aSize > bSize)
							return -1;
						else if (aSize == bSize)
							return 0;
						else
							return 1;
					}
				};

				for (String className : lexicalOrdering) {
					for (final ClassModel c : objectArrayFieldsClasses) {
						final ArrayList<FieldNameInfo> fields = c.getStructMembers();
						if (fields.size() > 0) {
							Collections.sort(fields, fieldSizeComparator);
							// Now compute the total size for the struct
							int alignTo = 0;
							int totalSize = 0;

							if (c.getClassWeAreModelling().getName().equals("scala.Tuple2")) {

								for (final FieldNameInfo f : fields) {
									final String fieldType = f.desc;
									final int fSize;
									if (fieldType.startsWith("L")) {
										fSize = 8; // TODO safe to hard-code size of pointer on device?
									} else
										fSize = getSizeOf(fieldType);
									if (fSize > alignTo)
										alignTo = fSize;
									totalSize += fSize;
								}
							} else {
								for (final FieldNameInfo f : fields) {
									// Record field offset for use while copying
									// Get field we will copy out of the kernel member object
									final Field rfield = getFieldFromClassHierarchy(c.getClassWeAreModelling(), f.name);

									long fieldOffset = UnsafeWrapper.objectFieldOffset(rfield);
									final String fieldType = f.desc;

									c.addStructMember(fieldOffset, TypeSpec.valueOf(fieldType), f.name);

									final int fSize = getSizeOf(fieldType);
									if (fSize > alignTo)
										alignTo = fSize;

									totalSize += fSize;
								}
							}

							// compute total size for OpenCL buffer
							int totalStructSize = 0;
							// if ((totalSize % alignTo) == 0) {
							totalStructSize = totalSize;
							// } else {
							//    // Pad up if necessary
							//    totalStructSize = ((totalSize / alignTo) + 1) * alignTo;
							// }
							c.setTotalStructSize(totalStructSize);
						}
					}
				}
			}
		}
	}

	public int getSizeOf(String desc) {
		if (desc.equals("Z")) desc = "B";

		if (desc.startsWith("L")) {
			for (final ClassModel cm : objectArrayFieldsClasses) {
				String classDesc = "L" + cm.getClassWeAreModelling().getName() + ";";
				if (classDesc.equals(desc))
					return cm.getTotalStructSize();
			}
		}

		return InstructionSet.TypeSpec.valueOf(desc).getSize();
	}

	private boolean noCL(ClassModelMethod m) {
		boolean found = m.getClassModel().getNoCLMethods().contains(m.getName());
		return found;
	}

	private boolean transformedMethod(ClassModelMethod m) {
		// TODO: DenseVector, etc
		if(m.getClassModel().toString().contains("scala.Tuple2"))
			return true;
		else
			return false;
	}

	private FieldEntry getSimpleGetterField(MethodModel method) {
		return method.getAccessorVariableFieldEntry();
	}

	public boolean shouldFallback() {
		return (fallback);
	}

	public List<ClassModel.ClassModelField> getReferencedClassModelFields() {
		return (referencedClassModelFields);
	}

	public List<Field> getReferencedFields() {
		return (referencedFields);
	}

	public List<MethodModel> getCalledMethods() {
		return calledMethods;
	}

	public Map<String, String> getReferencedFieldNames() {
		return (referencedFieldNames);
	}

	public Set<String> getArrayFieldAssignments() {
		return (arrayFieldAssignments);
	}

	public Set<String> getArrayFieldAccesses() {
		return (arrayFieldAccesses);
	}

	public Set<String> getArrayFieldArrayLengthUsed() {
		return (arrayFieldArrayLengthUsed);
	}

	public MethodModel getMethodModel() {
		return (methodModel);
	}

	public ClassModel getClassModel() {
		return (classModel);
	}

	private MethodModel lookForHardCodedMethod(MethodEntry _methodEntry, ClassModel classModel) {
		try {
			MethodModel hardCoded = classModel.checkForHardCodedMethods(
			                          _methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8(),
			                          _methodEntry.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8());
			if (hardCoded != null)
				return hardCoded;
		} catch (AparapiException a) {
			throw new RuntimeException(a);
		}
		return null;
	}

	/*
	 * Return the best call target MethodModel by looking in the class hierarchy
	 * @param _methodEntry MethodEntry for the desired target
	 * @return the fully qualified name such as "com_amd_javalabs_opencl_demo_PaternityTest$SimpleKernel__actuallyDoIt"
	 */
	public MethodModel getCallTarget(MethodEntry _methodEntry, boolean _isSpecial) {

		ClassModelMethod target = getClassModel().getMethod(_methodEntry, _isSpecial);
		boolean isMapped = Kernel.isMappedMethod(_methodEntry);

		if (logger.isLoggable(Level.FINE) && (target == null)) {
			logger.fine("Did not find call target: " + _methodEntry + " in " +
			            getClassModel().getClassWeAreModelling().getName() + " isMapped=" +
			            isMapped);
		}

		final String entryClassNameInDotForm =
		  _methodEntry.getClassEntry().getNameUTF8Entry().getUTF8().replace('/',
		      '.');
		final Set<ClassModel> matchingClassModels = new HashSet<ClassModel>();

		if (target == null) {
			// Look for member obj accessor calls
			for (final ClassModel memberObjClass : objectArrayFieldsClasses) {

				String memberObjClassName = memberObjClass.getClassWeAreModelling().getName();
				if (memberObjClassName.equals(entryClassNameInDotForm)) {
					MethodModel hardCoded = lookForHardCodedMethod(_methodEntry,
					                        memberObjClass);
					if (hardCoded != null) return hardCoded;

					target = memberObjClass.getMethod(_methodEntry, false);
					if (target != null)
						break;
				} else {
					if (memberObjClass.classNameMatches(entryClassNameInDotForm))
						matchingClassModels.add(memberObjClass);
				}
			}
		}

		if (target == null) {
			for (ClassModel possibleMatch : matchingClassModels) {
				MethodModel hardCoded = lookForHardCodedMethod(_methodEntry,
				                        possibleMatch);
				if (hardCoded != null) return hardCoded;

				target = possibleMatch.getMethod(_methodEntry, false);
				if (target != null)
					break;
			}
		}

		if (target != null) {
			for (final MethodModel m : calledMethods) {
				if (m.getMethod() == target) {
					if (logger.isLoggable(Level.FINE))
						logger.fine("selected from called methods = " + m.getName());

					return m;
				}
			}
		}

		// Search for static calls to other classes
		for (MethodModel m : calledMethods) {
			if (logger.isLoggable(Level.FINE))
				logger.fine("Searching for call target: " + _methodEntry + " in " + m.getName());
			if (m.getMethod().getName().equals(_methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8())
			    && m.getMethod().getDescriptor().equals(
			      _methodEntry.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8())) {
				if (logger.isLoggable(Level.FINE)) {
					logger.fine("Found " + m.getMethod().getClassModel().getClassWeAreModelling().getName() + "."
					            + m.getMethod().getName() + " " + m.getMethod().getDescriptor());
				}
				return m;
			}
		}

		assert target == null : "Should not have missed a method in calledMethods";

		return null;
	}

	Entrypoint cloneForKernel(Object _k) throws AparapiException {
		try {
			Entrypoint clonedEntrypoint = (Entrypoint) clone();
			clonedEntrypoint.kernelInstance = _k;
			return clonedEntrypoint;
		} catch (CloneNotSupportedException e) {
			throw new AparapiException(e);
		}
	}
}
