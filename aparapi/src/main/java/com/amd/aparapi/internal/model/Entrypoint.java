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
import com.amd.aparapi.classlibrary.*;
import com.amd.aparapi.internal.exception.*;
import com.amd.aparapi.internal.instruction.*;
import com.amd.aparapi.internal.instruction.InstructionSet.*;
import com.amd.aparapi.internal.model.ClassModel.*;
import com.amd.aparapi.internal.model.ClassModel.ConstantPool.*;
import com.amd.aparapi.internal.model.ClassModel.ConstantPool.MethodReferenceEntry.*;
import com.amd.aparapi.internal.util.*;

import com.amd.aparapi.internal.writer.*;
import com.amd.aparapi.internal.writer.JParameter.DIRECTION;
import com.amd.aparapi.internal.model.MethodModel.METHODTYPE;

import com.amd.aparapi.internal.model.CustomizedClassModels.CustomizedClassModelMatcher;
import com.amd.aparapi.internal.model.CustomizedClassModel.TypeParameters;

import java.io.File;
import java.lang.reflect.*;
import java.util.*;
import java.util.logging.*;
import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.lang.Class;
import java.lang.reflect.Field;

public class Entrypoint implements Cloneable {

	public final Config config;

	private static Logger logger = Logger.getLogger(Config.getLoggerName());

	private final List<ClassModel.ClassModelField> referencedClassModelFields = new
	ArrayList<ClassModel.ClassModelField>();

	private final List<Field> referencedFields = new ArrayList<Field>();

	private final Map<String, String> typeEnv;

	public String getEnvTypeHint(String varName) {
		if (typeEnv.containsKey(varName))
			return typeEnv.get(varName);
		return null;
	}

	private ClassModel classModel;

	private final CustomizedClassModels customizedClassModels;

	private final URLClassLoader appClassLoader;

	private final Object [] loadedClasses;

	private final Object [] systemClasses;

	public CustomizedClassModels getCustomizedClassModels() {
		return customizedClassModels;
	}

	private Object kernelInstance = null;

	private final boolean fallback = false;

	/*
	 * A mapping from fields including I/O to hints to their actual types. Some
	 * fields may have their types obscured by Scala bytecode obfuscation (e.g.
	 * scala.runtime.ObjectRef) but we can take guesses at the actual type based
	 * on the references to them (e.g. if they are cast to something).
	 */
	private final Map<String, String> referencedFieldNames = new HashMap<String, String>();
	private void addToReferencedFieldNames(String name, String hint) {
		referencedFieldNames.put(name, hint);
	}

	private Collection<JParameter> argumentList = null;

	private final Set<String> arrayFieldAssignments = new LinkedHashSet<String>();

	private final Set<String> arrayFieldAccesses = new LinkedHashSet<String>();

	// Derived classes
	private final Map<String, List<String> > derivedClasses = new HashMap<String, List<String> >();

	private Set<String> getKernelCalledInterfaceClasses() {
		return derivedClasses.keySet();
	}

	private void addOrUpdateDerivedClass(String base, List<String> clzList) {
		derivedClasses.put(base, clzList);
	}

	private List<String> getDerivedClasses(String base) {
		if (derivedClasses.containsKey(base))
			return derivedClasses.get(base);
		return null;
	}

	private boolean hasDerivedClassList(String clazz) {
		return derivedClasses.containsKey(clazz);
	}

	// Possible interface method implementation list
	private final Map<String, List<MethodModel> > interfaceMethodImpl = new HashMap<String, List<MethodModel> >();

	private boolean hasMethodImplList(String fullSig) {
		return interfaceMethodImpl.containsKey(fullSig);
	}

	private void addOrUpdateMethodImpl(String fullSig, MethodModel method) {
		List<MethodModel> implList = null;
		if (interfaceMethodImpl.containsKey(fullSig))
			implList = interfaceMethodImpl.get(fullSig);
		else
			implList = new ArrayList<MethodModel>();
		implList.add(method);
		interfaceMethodImpl.put(fullSig, implList);
	}

	public Set<String> getKernelCalledInterfaceMethods() {
		return interfaceMethodImpl.keySet();
	}

	public List<MethodModel> getMethodImpls(String fullSig) {
		if (interfaceMethodImpl.containsKey(fullSig))
			return interfaceMethodImpl.get(fullSig);
		return null;
	}

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

	private void addParameterClass(JParameter param) throws AparapiException {
		if (param.isPrimitive() == true)
			return ;

		param.init(this);
		if (customizedClassModels.hasClass(param.getTypeName()))
			addCustomizedClass(param.getClassModel());
		else {
			logger.fine("Add a non-modeled class " + param.getTypeName());
			addClass(param.getTypeName(), param.getDescArray());
		}

		for (final JParameter p : param.getTypeParameters())
			addParameterClass(p);

		return ;
	}

	private void addClass(String name, String[] desc) throws AparapiException {
		ClassModel model = null;

		try {
			model = getOrUpdateAllClassAccesses(name,
				new CustomizedClassModelMatcher(desc));
		} catch (Exception e) {
			logger.fine("Skip not found class " + name);
			return ;
		}

		addClass(name, model);
	}

	private void addClass(String name, ClassModel model) throws AparapiException {
		if (model instanceof CustomizedClassModel)
			addCustomizedClass((CustomizedClassModel) model);
		else {
			addToObjectArrayFieldsClasses(name, model);

			lexicalOrdering.add(name);
			allFieldsClasses.add(name, model);
		}
	}


	public void addCustomizedClass(CustomizedClassModel model) {
		customizedClassModels.addClass(model);
		allFieldsClasses.add(model.getClassName(), model);
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
	    CustomizedClassModelMatcher matcher) throws AparapiException {
		ClassModel memberClassModel = allFieldsClasses.get(className, ClassModel.getMatcher(className, matcher));
		Class<?> memberClass = null;
		if (memberClassModel == null) {
			try {
				memberClass = appClassLoader.loadClass(className);
			} catch (final Exception e) {
				if (logger.isLoggable(Level.INFO))
					logger.info("Cannot load: " + className);
				throw new AparapiException(e);
			}

			// Immediately add this class and all its supers if necessary
			memberClassModel = ClassModel.createClassModel(memberClass, this,
			                   matcher);

			logger.finest("adding class " + className);
			allFieldsClasses.add(className, memberClassModel);
			ClassModel superModel = memberClassModel.getSuperClazz();
			while (superModel != null) {
				// See if super is already added
				final ClassModel oldSuper = allFieldsClasses.get(
				                              superModel.getClassName(),
				                              new NameMatcher(superModel.getClassName()));
				if (oldSuper != null) {
					if (oldSuper != superModel) {
						memberClassModel.replaceSuperClazz(oldSuper);
						logger.finest("replaced super " + oldSuper.getClassName() + " for " +
						              className);
					}
				} else {
					allFieldsClasses.add(superModel.getClassName(), superModel);
					logger.finest("add new super " + superModel.getClassName() + " for " +
					              className);
				}
				superModel = superModel.getSuperClazz();
			}
		}

		return memberClassModel;
	}

	public ClassModelMethod resolveAccessorCandidate(final MethodCall _methodCall,
	    final MethodEntry _methodEntry) throws AparapiException {
		final String methodsActualClassName =
		  (_methodEntry.getClassEntry().getNameUTF8Entry().getUTF8()).replace('/', '.');

		if (_methodCall instanceof VirtualMethodCall &&
				!customizedClassModels.hasClass(methodsActualClassName)) {
			final Instruction callInstance = ((VirtualMethodCall) _methodCall).getInstanceReference();
			if (callInstance instanceof AccessArrayElement) {
				final AccessArrayElement arrayAccess = (AccessArrayElement) callInstance;
				final Instruction refAccess = arrayAccess.getArrayRef();

				// It is a call from a member obj array element
				logger.fine("Looking for class in accessor call: " + methodsActualClassName);

				final String methodName =
				  _methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
				final String methodDesc =
				  _methodEntry.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8();
				final String returnType = methodDesc.substring(methodDesc.lastIndexOf(')') + 1);

				final ClassModel memberClassModel = getOrUpdateAllClassAccesses(methodsActualClassName, 
						new CustomizedClassModelMatcher(null));

				// false = no invokespecial allowed here
				return memberClassModel.getMethod(_methodEntry, false);
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
			logger.severe("Referencing field " + accessedFieldName + " in " + className);
			throw new ClassParseException(ClassParseException.TYPE.OBJECTARRAYFIELDREFERENCE);
		}

		if (logger.isLoggable(Level.FINEST))
			logger.finest("Updating access: " + className + " field:" + accessedFieldName);

		final ClassModel memberClassModel = getOrUpdateAllClassAccesses(className, 
			new CustomizedClassModelMatcher(null));
		final Class<?> memberClass = memberClassModel.getClassWeAreModelling();
		ClassModel superCandidate = null;

		// We may add this field if no superclass match
		boolean add = true;

		// No exact match, look for a superclass
		for (final ClassModel c : allFieldsClasses) {
			if (logger.isLoggable(Level.FINEST))
				logger.finest(" super: " + c.getClassName() + " for " + className);
			if (c.isSuperClass(memberClass)) {
				if (logger.isLoggable(Level.FINE))
					logger.fine("selected super: " + c.getClassName() + " for " + className);
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
					            + memberClassModel.getClassName());
				}
			}
		}
	}

	/*
	 * Find a suitable call target in the kernel class, supers, object members or static calls
	 */
	public ClassModelMethod resolveCalledMethod(final MethodCall methodCall,
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
			String targetMethodOwner = methodEntry.getClassEntry().getNameUTF8Entry().getUTF8().replace('/', '.');
			final Set<ClassModel> possibleMatches = new HashSet<ClassModel>();

			for (ClassModel c : allFieldsClasses) {
				if (c.getClassName().equals(targetMethodOwner))
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
			ClassModel otherClassModel = getOrUpdateAllClassAccesses(otherClassName, 
				new CustomizedClassModelMatcher(null));

			//if (logger.isLoggable(Level.FINE)) {
			//   logger.fine("Looking for: " + methodEntry + " in other class " + otherClass.getName());
			//}
			// false because INVOKESPECIAL not allowed here
			m = otherClassModel.getMethod(methodEntry, false);
		}

		logger.fine("Selected method for: " + methodEntry + " is " + m);

		return m;
	}

	public Entrypoint(ClassModel _classModel, MethodModel _methodModel,
	                  Object _k, Collection<JParameter> params, 
										URLClassLoader _loader, Map<String, String> _typeEnv)
	throws AparapiException {
		classModel = _classModel;
		methodModel = _methodModel;
		kernelInstance = _k;
		argumentList = params;
		appClassLoader = _loader;
		typeEnv = _typeEnv;

		config = new Config();

		customizedClassModels = new CustomizedClassModels();

		final Map<ClassModelMethod, MethodModel> methodMap = new
		LinkedHashMap<ClassModelMethod, MethodModel>();

		boolean discovered = true;

		// Load com.amd.aparapi.classlibrary
		try {
			ClassLoader cl = CustomizedClassModel.class.getClassLoader();
			URL pkgURL = Thread.currentThread().getContextClassLoader()
				.getResource("com/amd/aparapi");
			String jarFileName = URLDecoder.decode(pkgURL.getFile(), "UTF-8");
			jarFileName = jarFileName.substring(5, jarFileName.indexOf("!"));
			JarFile jarFile = new JarFile(jarFileName);

			Enumeration<JarEntry> jarEntries = jarFile.entries();
			while (jarEntries.hasMoreElements()) {
				String entryName = jarEntries.nextElement().getName();
				if (entryName.startsWith("com/amd/aparapi/classlibrary") && 
						entryName.endsWith(".class") && 
						!entryName.contains("$")) {
					cl.loadClass(entryName.replace("/", ".").substring(0, entryName.length() - 6));
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Fail to load com.amd.aparapi.classlibaray");
		}

		// Build a loaded class list
		Class<?> loaderClazz = appClassLoader.getClass();
		while (loaderClazz != java.lang.ClassLoader.class)
			loaderClazz = loaderClazz.getSuperclass();
		try {
			Field fieldClazz = loaderClazz.getDeclaredField("classes");
			fieldClazz.setAccessible(true);

			// Load classes from user input classpath
			loadedClasses = ((Vector) fieldClazz.get(appClassLoader)).toArray();

			// Load com.amd.aparapi.classlibrary
			systemClasses = ((Vector) fieldClazz.get(CustomizedClassModel.class.getClassLoader())).toArray();
		} catch (Exception e) {
			throw new RuntimeException("Fail to load class list");
		}

		// Traverse customized class models from user
		ArrayList<String> customizedClassFileList = findDerivedClasses("CustomizedClassModel");
		for (String name : customizedClassFileList) {
			try {
				Class<?> clazz = appClassLoader.loadClass(name);
				@SuppressWarnings("unchecked")
				Class<CustomizedClassModel> customizedClass = (Class<CustomizedClassModel>) clazz;
				Constructor<?> cstr = customizedClass.getConstructor();
				addCustomizedClass((CustomizedClassModel) cstr.newInstance());
			} catch (Exception e) {
				throw new RuntimeException("Cannot load customized class model from user input class path " + 
					name + ": " + e);
			}
		}

		// Traverse customized class models from system
		ArrayList<String> systemCustomizedClassFileList = findDefaultCustomizedClassModels();
		for (String name : systemCustomizedClassFileList) {
			try {
				Class<?> clazz = Class.forName(name);
				@SuppressWarnings("unchecked")
				Class<CustomizedClassModel> customizedClass = (Class<CustomizedClassModel>) clazz;
				Constructor<?> cstr = customizedClass.getConstructor();
				addCustomizedClass((CustomizedClassModel) cstr.newInstance());
			} catch (Exception e) {
				throw new RuntimeException("Cannot load customized class model from system " + 
					name + ": " + e);
			}
		}

		if (logger.isLoggable(Level.FINEST)) {
			logger.finest("Loaded CustomizedClassModels:");
			for (String s : customizedClassModels.getClassList())
				System.err.println(s);
		}

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

		// Initialize and add customized classes if necessary
		for (final JParameter param : params)
			addParameterClass(param);

		// Add local variable used classes FIXME: Class w. type parameter matching
		for (final String varName : typeEnv.keySet()) {
			if (typeEnv.get(varName) == null || typeEnv.get(varName).contains("String"))
				continue;

			JParameter param = JParameter.createParameter(typeEnv.get(varName), varName, JParameter.DIRECTION.IN);
			if (!customizedClassModels.hasClass(param.getTypeName()))
				addParameterClass(param);
		}

		// Collect all interface methods called from the kernel and their implementations
		// TODO: Support multi-level inheritance. i.e. interface A -> B extends A -> [C] extends B
		for (final I_INVOKEINTERFACE interfaceCall : methodModel.getInterfaceMethodCalls()) {
			final InterfaceMethodEntry methodEntry = interfaceCall.getConstantPoolInterfaceMethodEntry();
			final String clazzName = methodEntry.getClassEntry().getNameUTF8Entry().getUTF8().replace('/', '.');
			final String methodName = methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
			final String methodDesc = methodEntry.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8();
			final String fullSig = clazzName + "." + methodName + methodDesc;

			if (customizedClassModels.hasClass(clazzName))
				continue;

			// Avoid redundent work
			if (hasMethodImplList(fullSig))
				continue;

			List<String> clzList = null;

			// Avoid redundent class scanning
			if (hasDerivedClassList(clazzName))
				clzList = getDerivedClasses(clazzName);
			else {
				// Scan all loaded classes to find derived classes
				clzList = findDerivedClasses(clazzName);
				addOrUpdateDerivedClass(clazzName, clzList);
			}

			// Check if the base class has the method implementation
			Class<?> baseClazz = null;
			try {
				baseClazz = appClassLoader.loadClass(clazzName);
			} catch (final Exception e) {
				if (logger.isLoggable(Level.INFO))
					logger.info("Cannot load " + clazzName);
				throw new AparapiException(e);
			}
			ClassModel baseClassModel = ClassModel.createClassModel(baseClazz, this, 
					new CustomizedClassModelMatcher(null));
			ClassModelMethod baseMethodImpl = baseClassModel.getMethod(methodName, methodDesc);

			try {
				MethodModel method = null;
				if (baseMethodImpl.getCodeEntry() != null) {
					method = new LoadedMethodModel(baseMethodImpl, this);
					// FIXME: We want to generate method implementation for based class 
					// as well, but for some reason we cannot load method implementation from 
					// interface.
					//methodMap.put(baseMethodImpl, method);
					addOrUpdateMethodImpl(fullSig, method);
				}
				else {
					if (logger.isLoggable(Level.FINEST))
						logger.finest(fullSig + " has no implementation");
				}
			} catch (final Exception e) {
				throw new AparapiException(e);
			}
				
			// Create method models for overritten methods in derived classes
			for (final String derivedClazzName : clzList) {
				Class<?> derivedClazz = null;
				try {
					derivedClazz = appClassLoader.loadClass(derivedClazzName);
				} catch (final Exception e) {
					if (logger.isLoggable(Level.INFO))
						logger.info("Cannot load " + derivedClazzName);
					throw new AparapiException(e);
				}
				ClassModel derivedClassModel = ClassModel.createClassModel(derivedClazz, this, 
						new CustomizedClassModelMatcher(null));

				MethodModel method = null;
				if (derivedClassModel instanceof CustomizedClassModel)
					method = ((CustomizedClassModel) derivedClassModel).getCustomizedMethod(methodName);
				else {
					ClassModelMethod methodImpl = derivedClassModel.getMethod(methodName, methodDesc);
					if (methodImpl == null) // Skip the derived class without the overrided method
						continue;
					method = new LoadedMethodModel(methodImpl, this);

					// Add interface implementations to methodMap
					methodMap.put(methodImpl, method);
				}

				// Skip the derived class without the overrided method
				if (method == null)
					continue;

				addOrUpdateMethodImpl(fullSig, method);

				// Add derived class to customized class list
				derivedClassModel.setSuperClazz(baseClassModel);
				addClass(derivedClazzName, derivedClassModel);

				methodModel.getCalledMethods().add(method);
			}
		}

		if (logger.isLoggable(Level.FINE)) {
			Set<String> baseSet = getKernelCalledInterfaceClasses();
			for (final String base : baseSet) {			
				String msg = "Interface class " + base + " has implemented by ";
				List<String> derivedList = getDerivedClasses(base);
				for (final String derived : derivedList)
					msg += derived + " ";
				logger.fine(msg);
			}

			Set<String> itfMethodSet = getKernelCalledInterfaceMethods();
			for (final String sig : itfMethodSet) {
				List<MethodModel> methodImpls = getMethodImpls(sig);
				String msg = "Interface method " + sig + " has " + methodImpls.size() + 
						" possible implementations ";
				for (final MethodModel impl : methodImpls)
					msg += impl.getName() + " ";
				logger.fine(msg);
			}
		}

		// Collect all methods called directly from kernel's run method
		for (final MethodCall methodCall : methodModel.getMethodCalls()) {
			logger.finest("In-kernel method call: " + methodCall);
			ClassModelMethod m = resolveCalledMethod(methodCall, classModel);
			if ((m != null) && !methodMap.keySet().contains(m) && !noCL(m)) {
				final MethodModel target = new LoadedMethodModel(m, this);
				methodMap.put(m, target);
				methodModel.getCalledMethods().add(target);
				discovered = true;
				logger.finest("Collect method to be generated: " + target.getName());
			}
		}

		// methodMap now contains a list of method called by run itself().
		// Walk the whole graph of called methods and add them to the methodMap
		while (discovered) {
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
								logger.fine("repositioning : " + m.getClassModel().getClassName() + " " +
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

		calledMethods.addAll(methodMap.values());
		Collections.reverse(calledMethods);
		final List<MethodModel> methods = new ArrayList<MethodModel>(calledMethods);

		// add method to the calledMethods so we can include in this list
		methods.add(methodModel);
		final Set<String> fieldAssignments = new HashSet<String>();

		final Set<String> fieldAccesses = new HashSet<String>();

		// This is just a prepass that collects metadata, we don't actually write kernels at this point
		for (final MethodModel methodModel : methods) {
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
						System.out.println("Expecting AccessField, but found: " + child.toString());
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

					String signature;
					if (access instanceof ScalaGetObjectRefField) {
						ScalaGetObjectRefField scalaGet = (ScalaGetObjectRefField)access;
						I_CHECKCAST cast = scalaGet.getCast();
						signature = cast.getConstantPoolClassEntry().getNameUTF8Entry().getUTF8().replace('/', '.');
						addToReferencedFieldNames(accessedFieldName, "L" + signature);
					} else {
						// Get signature (class name) of the field.
						// Example: signature BlazeBroadcast for BlazeBroadcast[Tuple2[_,_]]
						signature = field.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8();
						if (signature.startsWith("L"))
							signature = signature.substring(1, signature.length() - 1);
						signature = signature.replace('/', '.');

						// trnasformed class. Generic type has to be fetched here.
						if (customizedClassModels.hasClass(signature)) {
							Instruction next = instruction.getNextPC();
							if (!(next instanceof I_INVOKEVIRTUAL)) // No type info.
								throw new RuntimeException("Expecting invokevirtual after getfield, but found " + next);

							String typeHint = findTypeHintForCustomizedClass(accessedFieldName, signature, next);
							addToReferencedFieldNames(accessedFieldName, typeHint);
						} else
							addToReferencedFieldNames(accessedFieldName, null);
					}
					logger.fine("AccessField field type= " + signature + " in " + methodModel.getName());

					// Add the customed class model for the referenced obj array FIXME: Not verify yet.
					if (signature.startsWith("[L")) {
						// Turn [Lcom/amd/javalabs/opencl/demo/DummyOOA; into com.amd.javalabs.opencl.demo.DummyOOA for example
						final String className = (signature.substring(2, signature.length() - 1)).replace('/', '.');

						final ClassModel arrayFieldModel = getOrUpdateAllClassAccesses(className, 
							new CustomizedClassModelMatcher(null));
						if (arrayFieldModel != null) {
							if (arrayFieldModel instanceof CustomizedClassModel) {
								addToReferencedFieldNames(accessedFieldName,
								                          "[" + ((CustomizedClassModel) arrayFieldModel).getDescriptor());
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
									return className.equals(model.getClassName());
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
							if (!className.equals(getClassModel().getClassName())
							    && (getFieldFromClassHierarchy(getClassModel().getClassWeAreModelling(),
							                                   accessedFieldName) == null)) {
								updateObjectMemberFieldAccesses(className, field);
							}
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
					if (!className.equals(getClassModel().getClassName())
					    && (getFieldFromClassHierarchy(getClassModel().getClassWeAreModelling(),
					                                   assignedFieldName) == null))
						updateObjectMemberFieldAccesses(className, field);
					else {

						if ((!Config.enablePUTFIELD) && methodModel.methodUsesPutfield() 
							&& methodModel.getMethodType() != METHODTYPE.SETTER)
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
						if (Kernel.isMappedMethod(methodEntry)) { // only do this for intrinsics

							if (Kernel.usesAtomic32(methodEntry)) {
								throw new RuntimeException("We don't support atomic builtin functions.");
								//setRequiresAtomics32Pragma(true);
							}

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
						else if (instruction.getPrevPC() instanceof AccessLocalVariable) { 
							// Check if we want type hint from this instruction
							// Pattern: aload -> invokevirtual

							Instruction inst = instruction.getPrevPC();
							int loadIdx = ((AccessLocalVariable) inst).getLocalVariableTableIndex();
							if (loadIdx <= 1) // Skip "this" and input data
								continue;

							// Backtrack to find corresponding astore
							while (inst != null) {
								if (inst instanceof AssignToLocalVariable) {
									int storeIdx = ((AccessLocalVariable) inst).getLocalVariableTableIndex();
										if (loadIdx == storeIdx)
											break;
								}
								inst = inst.getPrevPC();
							}
							if (inst == null)
								throw new RuntimeException("Cannot find aload/astore pair.");

							// Focus on pattern "invokevirtual/interface -> checkcast -> astore"
							if (!(inst.getPrevPC() instanceof I_CHECKCAST) ||
									(!(inst.getPrevPC().getPrevPC() instanceof I_INVOKEVIRTUAL) &&
									 !(inst.getPrevPC().getPrevPC() instanceof I_INVOKEINTERFACE))) {
								continue;
							}

							Instruction varInst = inst.getPrevPC().getPrevPC().getPrevPC();
							String varName = null;
							String typeHint = null;

							if (!(varInst instanceof AccessField))
								continue;

							varName = ((AccessField) varInst).getConstantPoolFieldEntry()
													.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
							typeHint = getReferencedFieldTypeHint(varName);
						
							if (typeHint == null)
								throw new RuntimeException("Variable " + varName + " is neither field nor argument.");

							final String clazzName = methodEntry.getClassEntry().getNameUTF8Entry().getUTF8();
							final String methodName = methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
							final String methodDesc = methodEntry.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8();
							final String realType = methodDesc.substring(methodDesc.lastIndexOf(')') + 1);
							if (!customizedClassModels.getSample(clazzName).hasMethod(methodName))
								throw new RuntimeException("Method " + methodName + " doesn't be modeled in class " + clazzName);
		
							typeHint = typeHint.replace(methodName, realType);
							addToReferencedFieldNames(varName, typeHint);
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

		// Process referenced fields to a list for KernelWriter
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
					if (typeHint != null) {
						ff.setTypeHint(typeHint);
						logger.fine("Field " + referencedFieldName + " has type hint " + typeHint);
					}
					else
						logger.fine("Field " + referencedFieldName + " has no type hint");
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
						logger.finest("adding = " + superModel.getClassName() + " fields into "
						              + memberObjClass.getClassName());
					}
					memberObjClass.getStructMembers().addAll(superModel.getStructMembers());
					superModel = superModel.getSuperClazz();
				}
			}
		}
	}

	public ArrayList<String> findDerivedClasses(String baseClass) {
		ArrayList<String> clzList = new ArrayList<String>();
		for (final Object clazz : loadedClasses) {
			boolean found = false;

			// Check interfaces
			Class<?> [] interfaceList = ((Class) clazz).getInterfaces();
			if (interfaceList.length == 0)
				continue ;
			for (final Class<?> itf : interfaceList) {
				if (itf.getName().equals(baseClass)) {
					clzList.add(((Class<?>) clazz).getName());
					found = true;
					break ;
				}
			}

			// Check super class
			if (found == false) {
				Class<?> superClazz = ((Class) clazz).getSuperclass();
				if (superClazz == null)
					continue ;
				if (superClazz.getName().contains(baseClass))
					clzList.add(((Class<?>) clazz).getName());
			}
		}
		return clzList;	
	}

	public ArrayList<String> findDefaultCustomizedClassModels() {
		ArrayList<String> clzList = new ArrayList<String>();
		for (final Object clazz : systemClasses) {
			Class<?> superClazz = ((Class) clazz).getSuperclass();
			if (superClazz == null)
				continue ;
			if (superClazz.getName().contains("CustomizedClassModel"))
				clzList.add(((Class<?>) clazz).getName());
		}
		return clzList;	
	}

	public int getSizeOf(String desc) {
		if (desc.equals("Z")) desc = "B";

		if (desc.startsWith("L")) {
			for (final ClassModel cm : objectArrayFieldsClasses) {
				String classDesc = "L" + cm.getClassName() + ";";
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

	private FieldEntry getSimpleGetterField(MethodModel method) {
		return method.getAccessorVariableFieldEntry();
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

	public String getReferencedFieldTypeHint(String name) {		
		if (referencedFieldNames.containsKey(name)) {
			return referencedFieldNames.get(name);
		}
		else
			return null;
	}

	public Map<String, String> getReferencedFieldNames() {
		return (referencedFieldNames);
	}

	public JParameter getArgument(String name) {
		for (JParameter param : argumentList) {
			if (param.getName().equals(name))
				return param;
		}
		return null;
	}

	public Collection<JParameter> getArguments() {
		return (argumentList);
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

	private MethodModel lookForCustomizedMethod(MethodEntry _methodEntry, ClassModel classModel) {
		try {
			MethodModel method = classModel.checkForCustomizedMethods(
			                          _methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8(),
			                          _methodEntry.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8());
			if (method != null)
				return method;
		} catch (AparapiException a) {
			throw new RuntimeException(a);
		}
		return null;
	}

	private String findTypeHintForCustomizedClass(
		String fieldName, 
		String signature, 
		Instruction inst
	) {
		String typeHint = null;
		boolean isArray = false;
		boolean fetchDirectly = false;

		// Search for the following pattern to find the type hint:
		// Primitive type: - invokevirtual
		// Hardcoded type: - invokevirutal -> cast -> invokevirtual
		//								 - invokevirtual -> cast -> astore

		assert (inst instanceof I_INVOKEVIRTUAL);

		MethodEntry fieldMethodEntry = ((I_INVOKEVIRTUAL) inst).getConstantPoolMethodEntry();
		String fieldMethodName = fieldMethodEntry.getNameAndTypeEntry()
												.getNameUTF8Entry().getUTF8();
		String returnType = fieldMethodEntry.getNameAndTypeEntry().getDescriptorUTF8Entry()
												.getUTF8().replace("()", "");

		if (!customizedClassModels.hasClass(signature) || 
				!customizedClassModels.getSample(signature).hasMethod(fieldMethodName)) {
			throw new RuntimeException(signature + "." + fieldMethodName + " is not modeled");
		}

		if(customizedClassModels.getSample(signature).getCustomizedMethod(fieldMethodName)
				.getMethodType() != METHODTYPE.GETTER) {
			//System.err.println("Skip method " + methodName);
			return "";
		}

		// Method returns a primitve type so fetch directly.
		if (Utils.isPrimitive(returnType))
			fetchDirectly = true;

		if (fetchDirectly || inst.getNextPC() instanceof I_CHECKCAST) { // Cast for array or object type
			I_CHECKCAST cast = null;
			if (!fetchDirectly) {
				inst = inst.getNextPC();
				cast = (I_CHECKCAST) inst;

				// Get generic type information by guessing the next cast instruction.
				// Example: generic type scala.Tuple2 for BlazeBroadcast[Tuple2[_,_]]
				typeHint = cast.getConstantPoolClassEntry().getNameUTF8Entry().getUTF8();
			}
			else
				typeHint = signature;
			String genericType = typeHint;
			if (genericType.startsWith("[")) {
				genericType = genericType.substring(1);
//				arrayFieldArrayLengthUsed.add(fieldName);
				isArray = true;
			}
			if (customizedClassModels.hasClass(genericType)) {
				String curFieldType = null;	
				curFieldType = getReferencedFieldTypeHint(fieldName);

				if (curFieldType == null) {
					// Add field type mapping to genericType for later used.
					// Example: scala.Tuple2 -> scala.Tuple2<_1, _2>
					curFieldType = customizedClassModels.getSample(genericType).getMethod2ParamMapping();
				}

				// Expect: invokevirtual/interface -> cast -> invokevirtual.
				if (fetchDirectly || cast.getNextPC() instanceof I_INVOKEVIRTUAL) {
					I_INVOKEVIRTUAL accessor = null;
					if (fetchDirectly)
						accessor = (I_INVOKEVIRTUAL) inst;
					else
						accessor = (I_INVOKEVIRTUAL) cast.getNextPC();

					final MethodEntry methodEntry = accessor.getConstantPoolMethodEntry();
					final String methodName = methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();;
					final String methodDesc = methodEntry.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8();;

					if (!customizedClassModels.getSample(genericType).hasMethod(methodName))
						throw new RuntimeException("Method " + methodName + " doesn't be modeled in class " + genericType);
			
					// Get field type by consulting the method call.
					// Example: scala/Tuple2._2$mcD$sp:()D has type D
					String realFieldType = null;
					if (accessor.getParentExpr() instanceof I_CHECKCAST) { // FIXME: Not be verified yet.
						I_CHECKCAST cast_to_real = (I_CHECKCAST) accessor.getParentExpr();
						realFieldType = cast_to_real.getConstantPoolClassEntry().getNameUTF8Entry().getUTF8();
					} else
						realFieldType = methodDesc.substring(methodDesc.lastIndexOf(')') + 1);

					if (isArray && !curFieldType.startsWith("["))
						curFieldType = '[' + curFieldType;
					typeHint = curFieldType.replace(methodName, realFieldType);
				}
				else if (cast.getNextPC() instanceof AssignToLocalVariable) // Do nothing at this time
					typeHint = curFieldType;
				else
					throw new RuntimeException("Expecting invokevirtual, but found " + cast.getNextPC());
			}
		}
		else if (inst.getNextPC() instanceof I_INVOKESTATIC) { // Boxed for scalar type
			inst = inst.getNextPC();
			I_INVOKESTATIC unbox = (I_INVOKESTATIC) inst;
			typeHint = unbox.getConstantPoolMethodEntry().getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
			if (typeHint.contains("Int"))
				typeHint = "I";
			else if (typeHint.contains("Double"))
				typeHint = "D";
			else if (typeHint.contains("Float"))
				typeHint = "F";
			else if (typeHint.contains("Long"))
				typeHint = "J";
		}
		else
			throw new RuntimeException("Expected cast/unbox, but found " + inst);

		return typeHint;
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
			            getClassModel().getClassName() + " isMapped=" +
			            isMapped);
		}

		final String entryClassNameInDotForm =
		  _methodEntry.getClassEntry().getNameUTF8Entry().getUTF8().replace('/', '.');
		final String targetMethodName = _methodEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
		final Set<ClassModel> matchingClassModels = new HashSet<ClassModel>();

		// Look for member obj accessor calls
		if (target == null) {
			for (final ClassModel memberObjClass : objectArrayFieldsClasses) {

				String memberObjClassName = memberObjClass.getClassName();
				if (memberObjClassName.equals(entryClassNameInDotForm)) {
					MethodModel method = lookForCustomizedMethod(_methodEntry,
					                        memberObjClass);
					if (method != null) return method;

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
				MethodModel method = lookForCustomizedMethod(_methodEntry,
				                        possibleMatch);
				if (method != null) return method;

				target = possibleMatch.getMethod(_methodEntry, false);
				if (target != null)
					break;
			}
		}

		for (final MethodModel m : calledMethods) {
			if (m.getMethod() == target) {
				logger.fine("selected from called methods = " + m.getName());
				return m;
			}
		}

		// Search for static calls to other classes
		for (MethodModel m : calledMethods) {
			logger.fine("Searching for call target: " + _methodEntry + " in " + m.getName());
			if (m.getMethod().getName().equals(targetMethodName)
			    && m.getMethod().getDescriptor().equals(
			      _methodEntry.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8())) {
				logger.fine("Found " + m.getMethod().getClassModel().getClassName() + "."
				            + m.getMethod().getName() + " " + m.getMethod().getDescriptor());
				return m;
			}
		}

		assert target == null : "Should not have missed a method in calledMethods";

		return null;
	}

	public MethodModel getCustomizedCallTarget(String methodClass, String methodName, Instruction refInst) {
		String entryClassNameInDotForm = methodClass.replace('/', '.');
		logger.fine("Searching for customized classes: " + entryClassNameInDotForm);
		if (!customizedClassModels.hasClass(entryClassNameInDotForm))
			logger.fine("No customized class model for " + entryClassNameInDotForm);
		else {
			List<CustomizedClassModel> cms = customizedClassModels.get(entryClassNameInDotForm);

			CustomizedClassModel cm = null;
			if (cms.size() == 2) // No or has only one possible generic type
				cm = cms.get(cms.size() - 1);
			else {
				// Looking at the first argument to figure out the generic type
				String varName = null;

				if (refInst instanceof CloneInstruction)
					refInst = ((CloneInstruction) refInst).getReal();

				if (refInst instanceof LocalVariableConstIndexLoad)
					varName = ((LocalVariableConstIndexLoad) refInst).getLocalVariableInfo().getVariableName();
				else if (refInst instanceof AccessArrayElement) {
					final AccessArrayElement arrayAccess = (AccessArrayElement) refInst;
					final Instruction refAccess = arrayAccess.getArrayRef();
					varName = ((AccessField) refAccess).getConstantPoolFieldEntry().getNameAndTypeEntry()
							.getNameUTF8Entry().getUTF8();
				}
				else if (refInst instanceof AccessField) {
					varName = ((AccessField) refInst).getConstantPoolFieldEntry()
						.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
				} 
				else if (refInst instanceof Return)
					varName = "j2faOut";
				else
					throw new RuntimeException("cannot find the first argument for " + methodName + " from: " + refInst);

				String typeHint = getEnvTypeHint(varName);
				if (typeHint == null)
					throw new RuntimeException("Variable " + varName + " must have explicity type hint");
				else if (!typeHint.contains("<"))
					throw new RuntimeException("Variable " + varName + " should have generic types");

				String [] gTypes = typeHint.substring(typeHint.indexOf("<") + 1, typeHint.indexOf(">")).split(",");
				for (int j = 0; j < gTypes.length; j += 1) {
					gTypes[j] = gTypes[j].replace("/", ".");
					if (gTypes[j].startsWith("L"))
						gTypes[j] = gTypes[j].substring(1, gTypes[j].length() - 1);
				}

				// Match the type hint to the customized class model
				for (CustomizedClassModel c : cms) {
					boolean match = true;
					for (int j = 0; j < gTypes.length; j += 1) {
						if (!c.getTypeParam(j).equals(gTypes[j])) {
							match = false;
							break;
						}
					}
					if (match == true) {
						cm = c;
						break;
					}
				}
				if (cm == null)
					throw new RuntimeException("Cannot find matched customized class model: " + varName);
			}

			if (cm.hasMethod(methodName)) {
				logger.fine("selected from customized methods = " + methodName);
				return cm.getCustomizedMethod(methodName);
			}
		}
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
