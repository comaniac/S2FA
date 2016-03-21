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
import com.amd.aparapi.internal.annotation.*;
import com.amd.aparapi.internal.exception.*;
import com.amd.aparapi.internal.instruction.InstructionSet.*;
import com.amd.aparapi.internal.model.ValueCache.ThrowingValueComputer;
import com.amd.aparapi.internal.model.ClassModel.AttributePool.*;
import com.amd.aparapi.internal.model.ClassModel.ConstantPool.*;
import com.amd.aparapi.internal.reader.*;

import com.amd.aparapi.internal.writer.JParameter;
import com.amd.aparapi.internal.model.CustomizedClassModels.CustomizedClassModelMatcher;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.logging.*;

/**
 * Class represents a ClassFile (MyClass.class).
 *
 * A ClassModel is constructed from an instance of a <code>java.lang.Class</code>.
 *
 * If the java class mode changes we may need to modify this to accommodate.
 *
 * @see <a href="http://java.sun.com/docs/books/jvms/second_edition/ClassFileFormat-Java5.pdf">Java 5 Class File Format</a>
+ * @see <a href="http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html"> Java 7 Class File Format</a>
 *
 * @author gfrost
 *
 */
public class LoadedClassModel extends ClassModel {

	/**
	 * Create a MethodModel for a given method name and signature.
	 *
	 * @param _name
	 * @param _signature
	 * @return
	 * @throws AparapiException
	 */
	@Override
	public MethodModel getMethodModel(String _name, String _signature) throws AparapiException {
		if (CacheEnabler.areCachesEnabled())
			return methodModelCache.computeIfAbsent(MethodKey.of(_name, _signature));
		else {
			final ClassModelMethod method = getMethod(_name, _signature);
			return new LoadedMethodModel(method);
		}
	}

	@Override
	public MethodModel checkForCustomizedMethods(String name, String desc) throws AparapiException {
		return null;
	}

	@Override
	public String getMangledClassName() {
		return getClassWeAreModelling().getName().replace('.', '_');
	}

	//   private ValueCache<MethodKey, MethodModel, AparapiException> methodModelCache = ValueCache.on(this::computeMethodModel);
	private ValueCache<MethodKey, MethodModel, AparapiException> methodModelCache = ValueCache
	.on(new ThrowingValueComputer<MethodKey, MethodModel, AparapiException>() {
		@Override public MethodModel compute(MethodKey key) throws AparapiException {
			return computeMethodModel(key);
		}
	});

	private MethodModel computeMethodModel(MethodKey methodKey) throws AparapiException {
		final ClassModelMethod method = getMethod(methodKey.getName(), methodKey.getSignature());
		return new LoadedMethodModel(method);
	}

	LoadedClassModel(Class<?> _class) throws ClassParseException {

		parse(_class);

		final Class<?> mySuper = _class.getSuperclass();
		// Find better way to do this check
		// The java.lang.Object test is for unit test framework to succeed - should
		// not occur in normal use
		if ((mySuper != null) && (!mySuper.getName().equals(Kernel.class.getName()))
		    && (!mySuper.getName().equals("java.lang.Object")))
			superClazz = ClassModel.createClassModel(mySuper, null, new CustomizedClassModelMatcher(null));
	}

	LoadedClassModel(InputStream _inputStream) throws ClassParseException {

		parse(_inputStream);

	}

	LoadedClassModel(Class<?> _clazz, byte[] _bytes) throws ClassParseException {
		clazz = _clazz;
		parse(new ByteArrayInputStream(_bytes));
	}

	@Override
	public boolean classNameMatches(String className) {
		return getClassWeAreModelling().getName().equals(className);
	}
}
