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
package com.amd.aparapi;

import com.amd.aparapi.annotation.*;
import com.amd.aparapi.exception.*;
import com.amd.aparapi.internal.model.CacheEnabler;
import com.amd.aparapi.internal.model.ValueCache;
import com.amd.aparapi.internal.model.ValueCache.ThrowingValueComputer;
import com.amd.aparapi.internal.model.ValueCache.ValueComputer;
import com.amd.aparapi.internal.model.ClassModel.ConstantPool.*;
import com.amd.aparapi.internal.util.*;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

/**
 * A <i>kernel</i> encapsulates a data parallel algorithm that will execute either on a GPU
 * (through conversion to OpenCL) or on a CPU via a Java Thread Pool.
 * <p>
 * To write a new kernel, a developer extends the <code>Kernel</code> class and overrides the <code>Kernel.run()</code> method.
 * To execute this kernel, the developer creates a new instance of it and calls <code>Kernel.execute(int globalSize)</code> with a suitable 'global size'. At runtime
 * Aparapi will attempt to convert the <code>Kernel.run()</code> method (and any method called directly or indirectly
 * by <code>Kernel.run()</code>) into OpenCL for execution on GPU devices made available via the OpenCL platform.
 * <p>
 * Note that <code>Kernel.run()</code> is not called directly. Instead,
 * the <code>Kernel.execute(int globalSize)</code> method will cause the overridden <code>Kernel.run()</code>
 * method to be invoked once for each value in the range <code>0...globalSize</code>.
 * <p>
 * On the first call to <code>Kernel.execute(int _globalSize)</code>, Aparapi will determine the EXECUTION_MODE of the kernel.
 * This decision is made dynamically based on two factors:
 * <ol>
 * <li>Whether OpenCL is available (appropriate drivers are installed and the OpenCL and Aparapi dynamic libraries are included on the system path).</li>
 * <li>Whether the bytecode of the <code>run()</code> method (and every method that can be called directly or indirectly from the <code>run()</code> method)
 *  can be converted into OpenCL.</li>
 * </ol>
 * <p>
 * Below is an example Kernel that calculates the square of a set of input values.
 * <p>
 * <blockquote><pre>
 *     class SquareKernel extends Kernel{
 *         private int values[];
 *         private int squares[];
 *         public SquareKernel(int values[]){
 *            this.values = values;
 *            squares = new int[values.length];
 *         }
 *         public void run() {
 *             int gid = getGlobalID();
 *             squares[gid] = values[gid]*values[gid];
 *         }
 *         public int[] getSquares(){
 *             return(squares);
 *         }
 *     }
 * </pre></blockquote>
 * <p>
 * To execute this kernel, first create a new instance of it and then call <code>execute(Range _range)</code>.
 * <p>
 * <blockquote><pre>
 *     int[] values = new int[1024];
 *     // fill values array
 *     Range range = Range.create(values.length); // create a range 0..1024
 *     SquareKernel kernel = new SquareKernel(values);
 *     kernel.execute(range);
 * </pre></blockquote>
 * <p>
 * When <code>execute(Range)</code> returns, all the executions of <code>Kernel.run()</code> have completed and the results are available in the <code>squares</code> array.
 * <p>
 * <blockquote><pre>
 *     int[] squares = kernel.getSquares();
 *     for (int i=0; i< values.length; i++){
 *        System.out.printf("%4d %4d %8d\n", i, values[i], squares[i]);
 *     }
 * </pre></blockquote>
 * <p>
 * A different approach to creating kernels that avoids extending Kernel is to write an anonymous inner class:
 * <p>
 * <blockquote><pre>
 *
 *     final int[] values = new int[1024];
 *     // fill the values array
 *     final int[] squares = new int[values.length];
 *     final Range range = Range.create(values.length);
 *
 *     Kernel kernel = new Kernel(){
 *         public void run() {
 *             int gid = getGlobalID();
 *             squares[gid] = values[gid]*values[gid];
 *         }
 *     };
 *     kernel.execute(range);
 *     for (int i=0; i< values.length; i++){
 *        System.out.printf("%4d %4d %8d\n", i, values[i], squares[i]);
 *     }
 *
 * </pre></blockquote>
 * <p>
 *
 * @author  gfrost AMD Javalabs
 * @version Alpha, 21/09/2010
 */
public abstract class Kernel implements Cloneable {

	private static Logger logger = Logger.getLogger(Config.getLoggerName());

	// comaniac: Explicit kernel name
	private String name;
	private static boolean useDefault = false;

	/**
	 * @return the kernel name
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param Explicit kernel name
	 */
	protected Kernel(String str) {
		name = str;
	}

	/**
	 * Default constructor
	 */
	protected Kernel() {
		if(!useDefault) {
			name = "Default_Kernel";
			useDefault = true;
		} else
			System.out.println("Error: If you want to use multiple kernels in one class, then you must explicit kernel names!!");
	}

	/**
	 *  We can use this Annotation to 'tag' intended local buffers.
	 *
	 *  So we can either annotate the buffer
	 *  <pre><code>
	 *  &#64Local int[] buffer = new int[1024];
	 *  </code></pre>
	 *   Or use a special suffix
	 *  <pre><code>
	 *  int[] buffer_$local$ = new int[1024];
	 *  </code></pre>
	 *
	 *  @see #LOCAL_SUFFIX
	 *
	 *
	 */
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Local {

	}

	/**
	 *  We can use this Annotation to 'tag' intended constant buffers.
	 *
	 *  So we can either annotate the buffer
	 *  <pre><code>
	 *  &#64Constant int[] buffer = new int[1024];
	 *  </code></pre>
	 *   Or use a special suffix
	 *  <pre><code>
	 *  int[] buffer_$constant$ = new int[1024];
	 *  </code></pre>
	 *
	 *  @see #LOCAL_SUFFIX
	 *
	 *
	 */
	@Retention(RetentionPolicy.RUNTIME)
	public @interface Constant {

	}

	/**
	 *
	 *  We can use this Annotation to 'tag' __private (unshared) array fields. Data in the __private address space in OpenCL is accessible only from
	 *  the current kernel instance.
	 *
	 *  To so mark a field with a buffer size of 99, we can either annotate the buffer
	 *  <pre><code>
	 *  &#64PrivateMemorySpace(99) int[] buffer = new int[99];
	 *  </code></pre>
	 *   Or use a special suffix
	 *  <pre><code>
	 *  int[] buffer_$private$99 = new int[99];
	 *  </code></pre>
	 *
	 *  <p>Note that any code which must be runnable in {@link EXECUTION_MODE#JTP} will fail to work correctly if it uses such an
	 *  array, as the array will be shared by all threads. The solution is to create a {@link NoCL} method called at the start of {@link #run()} which sets
	 *  the field to an array returned from a static <code>ThreadLocal<foo[]></code></p>. Please see <code>MedianKernel7x7</code> in the samples for an example.
	 *
	 *  @see #PRIVATE_SUFFIX
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.FIELD})
	public @interface PrivateMemorySpace {
		/** Size of the array used as __private buffer. */
		int value();
	}

	/**
	 * Annotation which can be applied to either a getter (with usual java bean naming convention relative to an instance field), or to any method
	 * with void return type, which prevents both the method body and any calls to the method being emitted in the generated OpenCL. (In the case of a getter, the
	 * underlying field is used in place of the NoCL getter method.) This allows for code specialization within a java/JTP execution path, for example to
	 * allow logging/breakpointing when debugging, or to apply ThreadLocal processing (see {@link PrivateMemorySpace}) in java to simulate OpenCL __private
	 * memory.
	 */
	@Retention(RetentionPolicy.RUNTIME)
	@Target({ElementType.METHOD, ElementType.FIELD})
	public @interface NoCL {
		// empty
	}

	/**
	 *  We can use this suffix to 'tag' intended local buffers.
	 *
	 *
	 *  So either name the buffer
	 *  <pre><code>
	 *  int[] buffer_$local$ = new int[1024];
	 *  </code></pre>
	 *  Or use the Annotation form
	 *  <pre><code>
	 *  &#64Local int[] buffer = new int[1024];
	 *  </code></pre>
	 */
	public final static String LOCAL_SUFFIX = "_$local$";

	/**
	 *  We can use this suffix to 'tag' intended constant buffers.
	 *
	 *
	 *  So either name the buffer
	 *  <pre><code>
	 *  int[] buffer_$constant$ = new int[1024];
	 *  </code></pre>
	 *  Or use the Annotation form
	 *  <pre><code>
	 *  &#64Constant int[] buffer = new int[1024];
	 *  </code></pre>
	 */
	public final static String CONSTANT_SUFFIX = "_$constant$";

	/**
	 *  We can use this suffix to 'tag' __private buffers.
	 *
	 *  <p>So either name the buffer
	 *  <pre><code>
	 *  int[] buffer_$private$32 = new int[32];
	 *  </code></pre>
	 *  Or use the Annotation form
	 *  <pre><code>
	 *  &#64PrivateMemorySpace(32) int[] buffer = new int[32];
	 *  </code></pre>
	 *
	 *  @see PrivateMemorySpace for a more detailed usage summary
	 */
	public final static String PRIVATE_SUFFIX = "_$private$";

	/**
	 * This annotation is for internal use only
	 */
	@Retention(RetentionPolicy.RUNTIME)
	protected @interface OpenCLDelegate {

	}

	/**
	 * This annotation is for internal use only
	 */
	@Retention(RetentionPolicy.RUNTIME)
	protected @interface OpenCLMapping {
	String mapTo() default "";

	boolean atomic32() default false;

	boolean atomic64() default false;
	}

	/**
	 * Delegates to either {@link java.lang.Math#acos(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/acos.html">acos(float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param a value to delegate to {@link java.lang.Math#acos(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/acos.html">acos(float)</a></code>
	  * @return {@link java.lang.Math#acos(double)} casted to float/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/acos.html">acos(float)</a></code>
	  *
	  * @see java.lang.Math#acos(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/acos.html">acos(float)</a></code>
	  */
	@OpenCLMapping(mapTo = "acos")
	protected float acos(float a) {
		return (float) Math.acos(a);
	}

	/**
	* Delegates to either {@link java.lang.Math#acos(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/acos.html">acos(double)</a></code> (OpenCL).
	 *
	 * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	 *
	 * @param a value to delegate to {@link java.lang.Math#acos(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/acos.html">acos(double)</a></code>
	 * @return {@link java.lang.Math#acos(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/acos.html">acos(double)</a></code>
	 *
	 * @see java.lang.Math#acos(double)
	 * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/acos.html">acos(double)</a></code>
	 */
	@OpenCLMapping(mapTo = "acos")
	protected double acos(double a) {
		return Math.acos(a);
	}

	/**
	 * Delegates to either {@link java.lang.Math#asin(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/asin.html">asin(float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f value to delegate to {@link java.lang.Math#asin(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/asin.html">asin(float)</a></code>
	  * @return {@link java.lang.Math#asin(double)} casted to float/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/asin.html">asin(float)</a></code>
	  *
	  * @see java.lang.Math#asin(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/asin.html">asin(float)</a></code>
	  */
	@OpenCLMapping(mapTo = "asin")
	protected float asin(float _f) {
		return (float) Math.asin(_f);
	}

	/**
	 * Delegates to either {@link java.lang.Math#asin(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/asin.html">asin(double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d value to delegate to {@link java.lang.Math#asin(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/asin.html">asin(double)</a></code>
	  * @return {@link java.lang.Math#asin(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/asin.html">asin(double)</a></code>
	  *
	  * @see java.lang.Math#asin(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/asin.html">asin(double)</a></code>
	  */
	@OpenCLMapping(mapTo = "asin")
	protected double asin(double _d) {
		return Math.asin(_d);
	}

	/**
	 * Delegates to either {@link java.lang.Math#atan(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan(float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f value to delegate to {@link java.lang.Math#atan(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan(float)</a></code>
	  * @return {@link java.lang.Math#atan(double)} casted to float/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan(float)</a></code>
	  *
	  * @see java.lang.Math#atan(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan(float)</a></code>
	  */
	@OpenCLMapping(mapTo = "atan")
	protected float atan(float _f) {
		return (float) Math.atan(_f);
	}

	/**
	 * Delegates to either {@link java.lang.Math#atan(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan(double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d value to delegate to {@link java.lang.Math#atan(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan(double)</a></code>
	  * @return {@link java.lang.Math#atan(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan(double)</a></code>
	  *
	  * @see java.lang.Math#atan(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan(double)</a></code>
	  */
	@OpenCLMapping(mapTo = "atan")
	protected double atan(double _d) {
		return Math.atan(_d);
	}

	/**
	 * Delegates to either {@link java.lang.Math#atan2(double, double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan2(float, float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f1 value to delegate to first argument of {@link java.lang.Math#atan2(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan2(float, float)</a></code>
	  * @param _f2 value to delegate to second argument of {@link java.lang.Math#atan2(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan2(float, float)</a></code>
	  * @return {@link java.lang.Math#atan2(double, double)} casted to float/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan2(float, float)</a></code>
	  *
	  * @see java.lang.Math#atan2(double, double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan2(float, float)</a></code>
	  */
	@OpenCLMapping(mapTo = "atan2")
	protected float atan2(float _f1, float _f2) {
		return (float) Math.atan2(_f1, _f2);
	}

	/**
	 * Delegates to either {@link java.lang.Math#atan2(double, double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan2(double, double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d1 value to delegate to first argument of {@link java.lang.Math#atan2(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan2(double, double)</a></code>
	  * @param _d2 value to delegate to second argument of {@link java.lang.Math#atan2(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan2(double, double)</a></code>
	  * @return {@link java.lang.Math#atan2(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan2(double, double)</a></code>
	  *
	  * @see java.lang.Math#atan2(double, double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atan.html">atan2(double, double)</a></code>
	  */
	@OpenCLMapping(mapTo = "atan2")
	protected double atan2(double _d1, double _d2) {
		return Math.atan2(_d1, _d2);
	}

	/**
	 * Delegates to either {@link java.lang.Math#ceil(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/ceil.html">ceil(float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f value to delegate to {@link java.lang.Math#ceil(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/ceil.html">ceil(float)</a></code>
	  * @return {@link java.lang.Math#ceil(double)} casted to float/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/ceil.html">ceil(float)</a></code>
	  *
	  * @see java.lang.Math#ceil(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/ceil.html">ceil(float)</a></code>
	  */
	@OpenCLMapping(mapTo = "ceil")
	protected float ceil(float _f) {
		return (float) Math.ceil(_f);
	}

	/**
	 * Delegates to either {@link java.lang.Math#ceil(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/ceil.html">ceil(double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d value to delegate to {@link java.lang.Math#ceil(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/ceil.html">ceil(double)</a></code>
	  * @return {@link java.lang.Math#ceil(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/ceil.html">ceil(double)</a></code>
	  *
	  * @see java.lang.Math#ceil(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/ceil.html">ceil(double)</a></code>
	  */
	@OpenCLMapping(mapTo = "ceil")
	protected double ceil(double _d) {
		return Math.ceil(_d);
	}

	/**
	 * Delegates to either {@link java.lang.Math#cos(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/cos.html">cos(float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f value to delegate to {@link java.lang.Math#cos(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/cos.html">cos(float)</a></code>
	  * @return {@link java.lang.Math#cos(double)} casted to float/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/cos.html">cos(float)</a></code>
	  *
	  * @see java.lang.Math#cos(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/cos.html">cos(float)</a></code>
	  */
	@OpenCLMapping(mapTo = "cos")
	protected float cos(float _f) {
		return (float) Math.cos(_f);
	}

	/**
	 * Delegates to either {@link java.lang.Math#cos(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/cos.html">cos(double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d value to delegate to {@link java.lang.Math#cos(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/cos.html">cos(double)</a></code>
	  * @return {@link java.lang.Math#cos(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/cos.html">cos(double)</a></code>
	  *
	  * @see java.lang.Math#cos(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/cos.html">cos(double)</a></code>
	  */
	@OpenCLMapping(mapTo = "cos")
	protected double cos(double _d) {
		return Math.cos(_d);
	}

	/**
	 * Delegates to either {@link java.lang.Math#exp(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/exp.html">exp(float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f value to delegate to {@link java.lang.Math#exp(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/exp.html">exp(float)</a></code>
	  * @return {@link java.lang.Math#exp(double)} casted to float/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/exp.html">exp(float)</a></code>
	  *
	  * @see java.lang.Math#exp(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/exp.html">exp(float)</a></code>
	  */
	@OpenCLMapping(mapTo = "exp")
	protected float exp(float _f) {
		return (float) Math.exp(_f);
	}

	/**
	 * Delegates to either {@link java.lang.Math#exp(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/exp.html">exp(double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d value to delegate to {@link java.lang.Math#exp(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/exp.html">exp(double)</a></code>
	  * @return {@link java.lang.Math#exp(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/exp.html">exp(double)</a></code>
	  *
	  * @see java.lang.Math#exp(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/exp.html">exp(double)</a></code>
	  */
	@OpenCLMapping(mapTo = "exp")
	protected double exp(double _d) {
		return Math.exp(_d);
	}

	/**
	 * Delegates to either {@link java.lang.Math#abs(float)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fabs.html">fabs(float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f value to delegate to {@link java.lang.Math#abs(float)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fabs.html">fabs(float)</a></code>
	  * @return {@link java.lang.Math#abs(float)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fabs.html">fabs(float)</a></code>
	  *
	  * @see java.lang.Math#abs(float)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fabs.html">fabs(float)</a></code>
	  */
	@OpenCLMapping(mapTo = "fabs")
	protected float abs(float _f) {
		return Math.abs(_f);
	}

	/**
	 * Delegates to either {@link java.lang.Math#abs(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fabs.html">fabs(double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d value to delegate to {@link java.lang.Math#abs(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fabs.html">fabs(double)</a></code>
	  * @return {@link java.lang.Math#abs(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fabs.html">fabs(double)</a></code>
	  *
	  * @see java.lang.Math#abs(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fabs.html">fabs(double)</a></code>
	  */
	@OpenCLMapping(mapTo = "fabs")
	protected double abs(double _d) {
		return Math.abs(_d);
	}

	/**
	 * Delegates to either {@link java.lang.Math#abs(int)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/abs.html">abs(int)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param n value to delegate to {@link java.lang.Math#abs(int)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/abs.html">abs(int)</a></code>
	  * @return {@link java.lang.Math#abs(int)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/abs.html">abs(int)</a></code>
	  *
	  * @see java.lang.Math#abs(int)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/abs.html">abs(int)</a></code>
	  */
	@OpenCLMapping(mapTo = "abs")
	protected int abs(int n) {
		return Math.abs(n);
	}

	/**
	 * Delegates to either {@link java.lang.Math#abs(long)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/abs.html">abs(long)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param n value to delegate to {@link java.lang.Math#abs(long)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/abs.html">abs(long)</a></code>
	  * @return {@link java.lang.Math#abs(long)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fabs.html">abs(long)</a></code>
	  *
	  * @see java.lang.Math#abs(long)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/abs.html">abs(long)</a></code>
	  */
	@OpenCLMapping(mapTo = "abs")
	protected long abs(long n) {
		return Math.abs(n);
	}

	/**
	 * Delegates to either {@link java.lang.Math#floor(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/abs.html">floor(float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f value to delegate to {@link java.lang.Math#floor(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/floor.html">floor(float)</a></code>
	  * @return {@link java.lang.Math#floor(double)} casted to float/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/floor.html">floor(float)</a></code>
	  *
	  * @see java.lang.Math#floor(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/floor.html">floor(float)</a></code>
	  */
	@OpenCLMapping(mapTo = "floor")
	protected float floor(float _f) {
		return (float) Math.floor(_f);
	}

	/**
	 * Delegates to either {@link java.lang.Math#floor(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/abs.html">floor(double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d value to delegate to {@link java.lang.Math#floor(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/floor.html">floor(double)</a></code>
	  * @return {@link java.lang.Math#floor(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/floor.html">floor(double)</a></code>
	  *
	  * @see java.lang.Math#floor(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/floor.html">floor(double)</a></code>
	  */
	@OpenCLMapping(mapTo = "floor")
	protected double floor(double _d) {
		return Math.floor(_d);
	}

	/**
	 * Delegates to either {@link java.lang.Math#max(float, float)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmax.html">fmax(float, float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f1 value to delegate to first argument of {@link java.lang.Math#max(float, float)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmax.html">fmax(float, float)</a></code>
	  * @param _f2 value to delegate to second argument of {@link java.lang.Math#max(float, float)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmax.html">fmax(float, float)</a></code>
	  * @return {@link java.lang.Math#max(float, float)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmax.html">fmax(float, float)</a></code>
	  *
	  * @see java.lang.Math#max(float, float)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmax.html">fmax(float, float)</a></code>
	  */
	@OpenCLMapping(mapTo = "fmax")
	protected float max(float _f1, float _f2) {
		return Math.max(_f1, _f2);
	}

	/**
	 * Delegates to either {@link java.lang.Math#max(double, double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmax.html">fmax(double, double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d1 value to delegate to first argument of {@link java.lang.Math#max(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmax.html">fmax(double, double)</a></code>
	  * @param _d2 value to delegate to second argument of {@link java.lang.Math#max(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmax.html">fmax(double, double)</a></code>
	  * @return {@link java.lang.Math#max(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmax.html">fmax(double, double)</a></code>
	  *
	  * @see java.lang.Math#max(double, double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmax.html">fmax(double, double)</a></code>
	  */
	@OpenCLMapping(mapTo = "fmax")
	protected double max(double _d1, double _d2) {
		return Math.max(_d1, _d2);
	}

	/**
	 * Delegates to either {@link java.lang.Math#max(int, int)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">max(int, int)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param n1 value to delegate to {@link java.lang.Math#max(int, int)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">max(int, int)</a></code>
	  * @param n2 value to delegate to {@link java.lang.Math#max(int, int)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">max(int, int)</a></code>
	  * @return {@link java.lang.Math#max(int, int)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">max(int, int)</a></code>
	  *
	  * @see java.lang.Math#max(int, int)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">max(int, int)</a></code>
	  */
	@OpenCLMapping(mapTo = "max")
	protected int max(int n1, int n2) {
		return Math.max(n1, n2);
	}

	/**
	 * Delegates to either {@link java.lang.Math#max(long, long)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">max(long, long)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param n1 value to delegate to first argument of {@link java.lang.Math#max(long, long)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">max(long, long)</a></code>
	  * @param n2 value to delegate to second argument of {@link java.lang.Math#max(long, long)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">max(long, long)</a></code>
	  * @return {@link java.lang.Math#max(long, long)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">max(long, long)</a></code>
	  *
	  * @see java.lang.Math#max(long, long)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">max(long, long)</a></code>
	  */
	@OpenCLMapping(mapTo = "max")
	protected long max(long n1, long n2) {
		return Math.max(n1, n2);
	}

	/**
	 * Delegates to either {@link java.lang.Math#min(float, float)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmin.html">fmin(float, float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f1 value to delegate to first argument of {@link java.lang.Math#min(float, float)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmin.html">fmin(float, float)</a></code>
	  * @param _f2 value to delegate to second argument of {@link java.lang.Math#min(float, float)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmin.html">fmin(float, float)</a></code>
	  * @return {@link java.lang.Math#min(float, float)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmin.html">fmin(float, float)</a></code>
	  *
	  * @see java.lang.Math#min(float, float)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmin.html">fmin(float, float)</a></code>
	  */
	@OpenCLMapping(mapTo = "fmin")
	protected float min(float _f1, float _f2) {
		return Math.min(_f1, _f2);
	}

	/**
	 * Delegates to either {@link java.lang.Math#min(double, double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmin.html">fmin(double, double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d1 value to delegate to first argument of {@link java.lang.Math#min(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmin.html">fmin(double, double)</a></code>
	  * @param _d2 value to delegate to second argument of {@link java.lang.Math#min(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmin.html">fmin(double, double)</a></code>
	  * @return {@link java.lang.Math#min(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmin.html">fmin(double, double)</a></code>
	  *
	  * @see java.lang.Math#min(double, double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/fmin.html">fmin(double, double)</a></code>
	  */
	@OpenCLMapping(mapTo = "fmin")
	protected double min(double _d1, double _d2) {
		return Math.min(_d1, _d2);
	}

	/**
	 * Delegates to either {@link java.lang.Math#min(int, int)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">min(int, int)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param n1 value to delegate to first argument of {@link java.lang.Math#min(int, int)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">min(int, int)</a></code>
	  * @param n2 value to delegate to second argument of {@link java.lang.Math#min(int, int)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">min(int, int)</a></code>
	  * @return {@link java.lang.Math#min(int, int)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">min(int, int)</a></code>
	  *
	  * @see java.lang.Math#min(int, int)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">min(int, int)</a></code>
	  */
	@OpenCLMapping(mapTo = "min")
	protected int min(int n1, int n2) {
		return Math.min(n1, n2);
	}

	/**
	 * Delegates to either {@link java.lang.Math#min(long, long)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">min(long, long)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param n1 value to delegate to first argument of {@link java.lang.Math#min(long, long)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">min(long, long)</a></code>
	  * @param n2 value to delegate to second argument of {@link java.lang.Math#min(long, long)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">min(long, long)</a></code>
	  * @return {@link java.lang.Math#min(long, long)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">min(long, long)</a></code>
	  *
	  * @see java.lang.Math#min(long, long)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/integerMax.html">min(long, long)</a></code>
	  */
	@OpenCLMapping(mapTo = "min")
	protected long min(long n1, long n2) {
		return Math.min(n1, n2);
	}

	/**
	 * Delegates to either {@link java.lang.Math#log(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/log.html">log(float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f value to delegate to {@link java.lang.Math#log(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/log.html">log(float)</a></code>
	  * @return {@link java.lang.Math#log(double)} casted to float/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/log.html">log(float)</a></code>
	  *
	  * @see java.lang.Math#log(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/log.html">log(float)</a></code>
	  */
	@OpenCLMapping(mapTo = "log")
	protected float log(float _f) {
		return (float) Math.log(_f);
	}

	/**
	 * Delegates to either {@link java.lang.Math#log(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/log.html">log(double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d value to delegate to {@link java.lang.Math#log(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/log.html">log(double)</a></code>
	  * @return {@link java.lang.Math#log(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/log.html">log(double)</a></code>
	  *
	  * @see java.lang.Math#log(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/log.html">log(double)</a></code>
	  */
	@OpenCLMapping(mapTo = "log")
	protected double log(double _d) {
		return Math.log(_d);
	}

	/**
	 * Delegates to either {@link java.lang.Math#pow(double, double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/pow.html">pow(float, float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f1 value to delegate to first argument of {@link java.lang.Math#pow(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/pow.html">pow(float, float)</a></code>
	  * @param _f2 value to delegate to second argument of {@link java.lang.Math#pow(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/pow.html">pow(float, float)</a></code>
	  * @return {@link java.lang.Math#pow(double, double)} casted to float/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/pow.html">pow(float, float)</a></code>
	  *
	  * @see java.lang.Math#pow(double, double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/pow.html">pow(float, float)</a></code>
	  */
	@OpenCLMapping(mapTo = "pow")
	protected float pow(float _f1, float _f2) {
		return (float) Math.pow(_f1, _f2);
	}

	/**
	 * Delegates to either {@link java.lang.Math#pow(double, double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/pow.html">pow(double, double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d1 value to delegate to first argument of {@link java.lang.Math#pow(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/pow.html">pow(double, double)</a></code>
	  * @param _d2 value to delegate to second argument of {@link java.lang.Math#pow(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/pow.html">pow(double, double)</a></code>
	  * @return {@link java.lang.Math#pow(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/pow.html">pow(double, double)</a></code>
	  *
	  * @see java.lang.Math#pow(double, double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/pow.html">pow(double, double)</a></code>
	  */
	@OpenCLMapping(mapTo = "pow")
	protected double pow(double _d1, double _d2) {
		return Math.pow(_d1, _d2);
	}

	/**
	 * Delegates to either {@link java.lang.Math#IEEEremainder(double, double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/remainder.html">remainder(float, float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f1 value to delegate to first argument of {@link java.lang.Math#IEEEremainder(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/remainder.html">remainder(float, float)</a></code>
	  * @param _f2 value to delegate to second argument of {@link java.lang.Math#IEEEremainder(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/remainder.html">remainder(float, float)</a></code>
	  * @return {@link java.lang.Math#IEEEremainder(double, double)} casted to float/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/remainder.html">remainder(float, float)</a></code>
	  *
	  * @see java.lang.Math#IEEEremainder(double, double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/remainder.html">remainder(float, float)</a></code>
	  */
	@OpenCLMapping(mapTo = "remainder")
	protected float IEEEremainder(float _f1, float _f2) {
		return (float) Math.IEEEremainder(_f1, _f2);
	}

	/**
	 * Delegates to either {@link java.lang.Math#IEEEremainder(double, double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/remainder.html">remainder(double, double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d1 value to delegate to first argument of {@link java.lang.Math#IEEEremainder(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/remainder.html">remainder(double, double)</a></code>
	  * @param _d2 value to delegate to second argument of {@link java.lang.Math#IEEEremainder(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/remainder.html">remainder(double, double)</a></code>
	  * @return {@link java.lang.Math#IEEEremainder(double, double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/remainder.html">remainder(double, double)</a></code>
	  *
	  * @see java.lang.Math#IEEEremainder(double, double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/remainder.html">remainder(double, double)</a></code>
	  */
	@OpenCLMapping(mapTo = "remainder")
	protected double IEEEremainder(double _d1, double _d2) {
		return Math.IEEEremainder(_d1, _d2);
	}

	/**
	 * Delegates to either {@link java.lang.Math#toRadians(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/radians.html">radians(float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f value to delegate to {@link java.lang.Math#toRadians(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/radians.html">radians(float)</a></code>
	  * @return {@link java.lang.Math#toRadians(double)} casted to float/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/radians.html">radians(float)</a></code>
	  *
	  * @see java.lang.Math#toRadians(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/radians.html">radians(float)</a></code>
	  */
	@OpenCLMapping(mapTo = "radians")
	protected float toRadians(float _f) {
		return (float) Math.toRadians(_f);
	}

	/**
	 * Delegates to either {@link java.lang.Math#toRadians(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/radians.html">radians(double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d value to delegate to {@link java.lang.Math#toRadians(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/radians.html">radians(double)</a></code>
	  * @return {@link java.lang.Math#toRadians(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/radians.html">radians(double)</a></code>
	  *
	  * @see java.lang.Math#toRadians(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/radians.html">radians(double)</a></code>
	  */
	@OpenCLMapping(mapTo = "radians")
	protected double toRadians(double _d) {
		return Math.toRadians(_d);
	}

	/**
	 * Delegates to either {@link java.lang.Math#toDegrees(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/degrees.html">degrees(float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f value to delegate to {@link java.lang.Math#toDegrees(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/degrees.html">degrees(float)</a></code>
	  * @return {@link java.lang.Math#toDegrees(double)} casted to float/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/degrees.html">degrees(float)</a></code>
	  *
	  * @see java.lang.Math#toDegrees(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/degrees.html">degrees(float)</a></code>
	  */
	@OpenCLMapping(mapTo = "degrees")
	protected float toDegrees(float _f) {
		return (float) Math.toDegrees(_f);
	}

	/**
	 * Delegates to either {@link java.lang.Math#toDegrees(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/degrees.html">degrees(double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d value to delegate to {@link java.lang.Math#toDegrees(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/degrees.html">degrees(double)</a></code>
	  * @return {@link java.lang.Math#toDegrees(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/degrees.html">degrees(double)</a></code>
	  *
	  * @see java.lang.Math#toDegrees(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/degrees.html">degrees(double)</a></code>
	  */
	@OpenCLMapping(mapTo = "degrees")
	protected double toDegrees(double _d) {
		return Math.toDegrees(_d);
	}

	/**
	 * Delegates to either {@link java.lang.Math#rint(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/rint.html">rint(float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f value to delegate to {@link java.lang.Math#rint(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/rint.html">rint(float)</a></code>
	  * @return {@link java.lang.Math#rint(double)} casted to float/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/rint.html">rint(float)</a></code>
	  *
	  * @see java.lang.Math#rint(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/rint.html">rint(float)</a></code>
	  */
	@OpenCLMapping(mapTo = "rint")
	protected float rint(float _f) {
		return (float) Math.rint(_f);
	}

	/**
	 * Delegates to either {@link java.lang.Math#rint(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/rint.html">rint(double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d value to delegate to {@link java.lang.Math#rint(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/rint.html">rint(double)</a></code>
	  * @return {@link java.lang.Math#rint(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/rint.html">rint(double)</a></code>
	  *
	  * @see java.lang.Math#rint(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/rint.html">rint(double)</a></code>
	  */
	@OpenCLMapping(mapTo = "rint")
	protected double rint(double _d) {
		return Math.rint(_d);
	}

	/**
	 * Delegates to either {@link java.lang.Math#round(float)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/round.html">round(float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f value to delegate to {@link java.lang.Math#round(float)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/round.html">round(float)</a></code>
	  * @return {@link java.lang.Math#round(float)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/round.html">round(float)</a></code>
	  *
	  * @see java.lang.Math#round(float)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/round.html">round(float)</a></code>
	  */
	@OpenCLMapping(mapTo = "round")
	protected int round(float _f) {
		return Math.round(_f);
	}

	/**
	 * Delegates to either {@link java.lang.Math#round(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/round.html">round(double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d value to delegate to {@link java.lang.Math#round(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/round.html">round(double)</a></code>
	  * @return {@link java.lang.Math#round(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/round.html">round(double)</a></code>
	  *
	  * @see java.lang.Math#round(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/round.html">round(double)</a></code>
	  */
	@OpenCLMapping(mapTo = "round")
	protected long round(double _d) {
		return Math.round(_d);
	}

	/**
	 * Delegates to either {@link java.lang.Math#sin(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sin.html">sin(float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f value to delegate to {@link java.lang.Math#sin(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sin.html">sin(float)</a></code>
	  * @return {@link java.lang.Math#sin(double)} casted to float/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sin.html">sin(float)</a></code>
	  *
	  * @see java.lang.Math#sin(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sin.html">sin(float)</a></code>
	  */
	@OpenCLMapping(mapTo = "sin")
	protected float sin(float _f) {
		return (float) Math.sin(_f);
	}

	/**
	 * Delegates to either {@link java.lang.Math#sin(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sin.html">sin(double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d value to delegate to {@link java.lang.Math#sin(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sin.html">sin(double)</a></code>
	  * @return {@link java.lang.Math#sin(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sin.html">sin(double)</a></code>
	  *
	  * @see java.lang.Math#sin(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sin.html">sin(double)</a></code>
	  */
	@OpenCLMapping(mapTo = "sin")
	protected double sin(double _d) {
		return Math.sin(_d);
	}

	/**
	 * Delegates to either {@link java.lang.Math#sqrt(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sqrt.html">sqrt(float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f value to delegate to {@link java.lang.Math#sqrt(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sqrt.html">sqrt(float)</a></code>
	  * @return {@link java.lang.Math#sqrt(double)} casted to float/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sqrt.html">sqrt(float)</a></code>
	  *
	  * @see java.lang.Math#sqrt(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sqrt.html">sqrt(float)</a></code>
	  */
	@OpenCLMapping(mapTo = "sqrt")
	protected float sqrt(float _f) {
		return (float) Math.sqrt(_f);
	}

	/**
	 * Delegates to either {@link java.lang.Math#sqrt(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sqrt.html">sqrt(double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d value to delegate to {@link java.lang.Math#sqrt(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sqrt.html">sqrt(double)</a></code>
	  * @return {@link java.lang.Math#sqrt(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sqrt.html">sqrt(double)</a></code>
	  *
	  * @see java.lang.Math#sqrt(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sqrt.html">sqrt(double)</a></code>
	  */
	@OpenCLMapping(mapTo = "sqrt")
	protected double sqrt(double _d) {
		return Math.sqrt(_d);
	}

	/**
	 * Delegates to either {@link java.lang.Math#tan(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/tan.html">tan(float)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f value to delegate to {@link java.lang.Math#tan(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/tan.html">tan(float)</a></code>
	  * @return {@link java.lang.Math#tan(double)} casted to float/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/tan.html">tan(float)</a></code>
	  *
	  * @see java.lang.Math#tan(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/tan.html">tan(float)</a></code>
	  */
	@OpenCLMapping(mapTo = "tan")
	protected float tan(float _f) {
		return (float) Math.tan(_f);
	}

	/**
	 * Delegates to either {@link java.lang.Math#tan(double)} (Java) or <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/tan.html">tan(double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d value to delegate to {@link java.lang.Math#tan(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/tan.html">tan(double)</a></code>
	  * @return {@link java.lang.Math#tan(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/tan.html">tan(double)</a></code>
	  *
	  * @see java.lang.Math#tan(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/tan.html">tan(double)</a></code>
	  */
	@OpenCLMapping(mapTo = "tan")
	protected double tan(double _d) {
		return Math.tan(_d);
	}

	// the following rsqrt and native_sqrt and native_rsqrt don't exist in java Math
	// but added them here for nbody testing, not sure if we want to expose them
	/**
	 * Computes  inverse square root using {@link java.lang.Math#sqrt(double)} (Java) or delegates to <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sqrt.html">rsqrt(double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _f value to delegate to {@link java.lang.Math#sqrt(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sqrt.html">rsqrt(double)</a></code>
	  * @return <code>( 1.0f / {@link java.lang.Math#sqrt(double)} casted to float )</code>/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sqrt.html">rsqrt(double)</a></code>
	  *
	  * @see java.lang.Math#sqrt(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sqrt.html">rsqrt(double)</a></code>
	  */
	@OpenCLMapping(mapTo = "rsqrt")
	protected float rsqrt(float _f) {
		return (1.0f / (float) Math.sqrt(_f));
	}

	/**
	 * Computes  inverse square root using {@link java.lang.Math#sqrt(double)} (Java) or delegates to <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sqrt.html">rsqrt(double)</a></code> (OpenCL).
	  *
	  * User should note the differences in precision between Java and OpenCL's implementation of arithmetic functions to determine whether the difference in precision is acceptable.
	  *
	  * @param _d value to delegate to {@link java.lang.Math#sqrt(double)}/<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sqrt.html">rsqrt(double)</a></code>
	  * @return <code>( 1.0f / {@link java.lang.Math#sqrt(double)} )</code> /<code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sqrt.html">rsqrt(double)</a></code>
	  *
	  * @see java.lang.Math#sqrt(double)
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/sqrt.html">rsqrt(double)</a></code>
	  */
	@OpenCLMapping(mapTo = "rsqrt")
	protected double rsqrt(double _d) {
		return (1.0 / Math.sqrt(_d));
	}

	@OpenCLMapping(mapTo = "native_sqrt")
	private float native_sqrt(float _f) {
		int j = Float.floatToIntBits(_f);
		j = ((1 << 29) + (j >> 1)) - (1 << 22) - 0x4c00;
		return (Float.intBitsToFloat(j));
		// could add more precision using one iteration of newton's method, use the following
	}

	@OpenCLMapping(mapTo = "native_rsqrt")
	private float native_rsqrt(float _f) {
		int j = Float.floatToIntBits(_f);
		j = 0x5f3759df - (j >> 1);
		final float x = (Float.intBitsToFloat(j));
		return x;
		// if want more precision via one iteration of newton's method, use the following
		// float fhalf = 0.5f*_f;
		// return (x *(1.5f - fhalf * x * x));
	}

	// Hacked from AtomicIntegerArray.getAndAdd(i, delta)
	/**
	 * Atomically adds <code>_delta</code> value to <code>_index</code> element of array <code>_arr</code> (Java) or delegates to <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atomic_add.html">atomic_add(volatile int*, int)</a></code> (OpenCL).
	  *
	  *
	  * @param _arr array for which an element value needs to be atomically incremented by <code>_delta</code>
	  * @param _index index of the <code>_arr</code> array that needs to be atomically incremented by <code>_delta</code>
	  * @param _delta value by which <code>_index</code> element of <code>_arr</code> array needs to be atomically incremented
	  * @return previous value of <code>_index</code> element of <code>_arr</code> array
	  *
	  * @see <code><a href="http://www.khronos.org/registry/cl/sdk/1.1/docs/man/xhtml/atomic_add.html">atomic_add(volatile int*, int)</a></code>
	  */
	@OpenCLMapping(atomic32 = true)
	protected int atomicAdd(int[] _arr, int _index, int _delta) {
		if (!Config.disableUnsafe)
			return UnsafeWrapper.atomicAdd(_arr, _index, _delta);
		else {
			synchronized (_arr) {
				final int previous = _arr[_index];
				_arr[_index] += _delta;
				return previous;
			}
		}
	}

	@OpenCLMapping(mapTo = "hypot")
	protected float hypot(final float a, final float b) {
		return (float) Math.hypot(a, b);
	}

	@OpenCLMapping(mapTo = "hypot")
	protected double hypot(final double a, final double b) {
		return Math.hypot(a, b);
	}

	final static Map<String, String> typeToLetterMap = new HashMap<String, String>();

	static {
		// only primitive types for now
		typeToLetterMap.put("double", "D");
		typeToLetterMap.put("float", "F");
		typeToLetterMap.put("int", "I");
		typeToLetterMap.put("long", "J");
		typeToLetterMap.put("boolean", "Z");
		typeToLetterMap.put("byte", "B");
		typeToLetterMap.put("char", "C");
		typeToLetterMap.put("short", "S");
		typeToLetterMap.put("void", "V");
	}

	private static String descriptorToReturnTypeLetter(String desc) {
		// find the letter after the closed parenthesis
		return desc.substring(desc.lastIndexOf(')') + 1);
	}

	private static String getReturnTypeLetter(Method meth) {
		return toClassShortNameIfAny(meth.getReturnType());
	}

	private static String toClassShortNameIfAny(final Class<?> retClass) {
		if (retClass.isArray())
			return "[" + toClassShortNameIfAny(retClass.getComponentType());
		final String strRetClass = retClass.toString();
		final String mapping = typeToLetterMap.get(strRetClass);
		// System.out.println("strRetClass = <" + strRetClass + ">, mapping = " + mapping);
		if (mapping == null)
			return "[" + retClass.getName() + ";";
		return mapping;
	}

	public static String getMappedMethodName(MethodReferenceEntry _methodReferenceEntry) {
		if (CacheEnabler.areCachesEnabled())
			return getProperty(mappedMethodNamesCache, _methodReferenceEntry, null);
		String mappedName = null;
		final String name = _methodReferenceEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8();
		Class<?> currentClass = _methodReferenceEntry.getOwnerClassModel().getClassWeAreModelling();
		while (currentClass != Object.class) {
			for (final Method kernelMethod : currentClass.getDeclaredMethods()) {
				if (kernelMethod.isAnnotationPresent(OpenCLMapping.class)) {
					// ultimately, need a way to constrain this based upon signature (to disambiguate abs(float) from abs(int);
					// for Alpha, we will just disambiguate based on the return type
					if (false) {
						System.out.println("kernelMethod is ... " + kernelMethod.toGenericString());
						System.out.println("returnType = " + kernelMethod.getReturnType());
						System.out.println("returnTypeLetter = " + getReturnTypeLetter(kernelMethod));
						System.out.println("kernelMethod getName = " + kernelMethod.getName());
						System.out.println("methRefName = " + name + " descriptor = "
						                   + _methodReferenceEntry.getNameAndTypeEntry().getDescriptorUTF8Entry().getUTF8());
						System.out.println("descToReturnTypeLetter = "
						                   + descriptorToReturnTypeLetter(_methodReferenceEntry.getNameAndTypeEntry().getDescriptorUTF8Entry()
						                       .getUTF8()));
					}
					if (toSignature(_methodReferenceEntry).equals(toSignature(kernelMethod))) {
						final OpenCLMapping annotation = kernelMethod.getAnnotation(OpenCLMapping.class);
						final String mapTo = annotation.mapTo();
						if (!mapTo.equals("")) {
							mappedName = mapTo;
							// System.out.println("mapTo = " + mapTo);
						}
					}
				}
			}
			if (mappedName != null)
				break;
			currentClass = currentClass.getSuperclass();
		}
		// System.out.println("... in getMappedMethodName, returning = " + mappedName);
		return (mappedName);
	}

	public static boolean isMappedMethod(MethodReferenceEntry methodReferenceEntry) {
		if (CacheEnabler.areCachesEnabled())
			return getBoolean(mappedMethodFlags, methodReferenceEntry);
		System.err.println("Looking for mapped methods");
		boolean isMapped = false;
		for (final Method kernelMethod : Kernel.class.getDeclaredMethods()) {
			if (kernelMethod.isAnnotationPresent(OpenCLMapping.class)) {
				System.err.println("Comparing " +
				                   methodReferenceEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8() + " " +
				                   kernelMethod.getName());
				if (methodReferenceEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8().equals(
				      kernelMethod.getName())) {

					// well they have the same name ;)
					isMapped = true;
				}
			}
		}
		return (isMapped);
	}

	public static boolean isOpenCLDelegateMethod(MethodReferenceEntry methodReferenceEntry) {
		if (CacheEnabler.areCachesEnabled())
			return getBoolean(openCLDelegateMethodFlags, methodReferenceEntry);
		boolean isMapped = false;
		for (final Method kernelMethod : Kernel.class.getDeclaredMethods()) {
			if (kernelMethod.isAnnotationPresent(OpenCLDelegate.class)) {
				if (methodReferenceEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8().equals(
				      kernelMethod.getName())) {

					// well they have the same name ;)
					isMapped = true;
				}
			}
		}
		return (isMapped);
	}

	public static boolean usesAtomic32(MethodReferenceEntry methodReferenceEntry) {
		if (CacheEnabler.areCachesEnabled())
			return getProperty(atomic32Cache, methodReferenceEntry, false);
		for (final Method kernelMethod : Kernel.class.getDeclaredMethods()) {
			if (kernelMethod.isAnnotationPresent(OpenCLMapping.class)) {
				if (methodReferenceEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8().equals(
				      kernelMethod.getName())) {
					final OpenCLMapping annotation = kernelMethod.getAnnotation(OpenCLMapping.class);
					return annotation.atomic32();
				}
			}
		}
		return (false);
	}

	// For alpha release atomic64 is not supported
	public static boolean usesAtomic64(MethodReferenceEntry methodReferenceEntry) {
		//      if (CacheEnabler.areCachesEnabled())
		//      return getProperty(atomic64Cache, methodReferenceEntry, false);
		//for (java.lang.reflect.Method kernelMethod : Kernel.class.getDeclaredMethods()) {
		//   if (kernelMethod.isAnnotationPresent(Kernel.OpenCLMapping.class)) {
		//      if (methodReferenceEntry.getNameAndTypeEntry().getNameUTF8Entry().getUTF8().equals(kernelMethod.getName())) {
		//         OpenCLMapping annotation = kernelMethod.getAnnotation(Kernel.OpenCLMapping.class);
		//           return annotation.atomic64();
		//      }
		//   }
		//}
		return (false);
	}

	// the flag useNullForLocalSize is useful for testing that what we compute for localSize is what OpenCL
	// would also compute if we passed in null.  In non-testing mode, we just call execute with the
	// same localSize that we computed in getLocalSizeJNI.  We don't want do publicize these of course.
	// GRF we can't access this from test classes without exposing in in javadoc so I left the flag but made the test/set of the flag reflectively
	boolean useNullForLocalSize = false;

	private static final ValueCache<Class<?>, Map<String, Boolean>, RuntimeException> mappedMethodFlags
	  = markedWith(OpenCLMapping.class);

	private static final ValueCache<Class<?>, Map<String, Boolean>, RuntimeException>
	openCLDelegateMethodFlags = markedWith(OpenCLDelegate.class);

	private static final ValueCache<Class<?>, Map<String, Boolean>, RuntimeException> atomic32Cache =
	cacheProperty(new ValueComputer<Class<?>, Map<String, Boolean>>() {
		@Override
		public Map<String, Boolean> compute(Class<?> key) {
			Map<String, Boolean> properties = new HashMap<>();
			for (final Method method : key.getDeclaredMethods()) {
				if (isRelevant(method) && method.isAnnotationPresent(OpenCLMapping.class))
					properties.put(toSignature(method), method.getAnnotation(OpenCLMapping.class).atomic32());
			}
			return properties;
		}
	});

	private static final ValueCache<Class<?>, Map<String, Boolean>, RuntimeException> atomic64Cache =
	cacheProperty(new ValueComputer<Class<?>, Map<String, Boolean>>() {
		@Override
		public Map<String, Boolean> compute(Class<?> key) {
			Map<String, Boolean> properties = new HashMap<>();
			for (final Method method : key.getDeclaredMethods()) {
				if (isRelevant(method) && method.isAnnotationPresent(OpenCLMapping.class))
					properties.put(toSignature(method), method.getAnnotation(OpenCLMapping.class).atomic64());
			}
			return properties;
		}
	});

	private static boolean getBoolean(ValueCache<Class<?>, Map<String, Boolean>, RuntimeException>
	                                  methodNamesCache,
	                                  MethodReferenceEntry methodReferenceEntry) {
		return getProperty(methodNamesCache, methodReferenceEntry, false);
	}

	private static <A extends Annotation> ValueCache<Class<?>, Map<String, Boolean>, RuntimeException>
	markedWith(
	  final Class<A> annotationClass) {
		return cacheProperty(new ValueComputer<Class<?>, Map<String, Boolean>>() {
			@Override
			public Map<String, Boolean> compute(Class<?> key) {
				Map<String, Boolean> markedMethodNames = new HashMap<>();
				for (final Method method : key.getDeclaredMethods())
					markedMethodNames.put(toSignature(method), method.isAnnotationPresent(annotationClass));
				return markedMethodNames;
			}
		});
	}

	static String toSignature(Method method) {
		return method.getName() + getArgumentsLetters(method) + getReturnTypeLetter(method);
	}

	private static String getArgumentsLetters(Method method) {
		StringBuilder sb = new StringBuilder("(");
		for (Class<?> parameterClass : method.getParameterTypes())
			sb.append(toClassShortNameIfAny(parameterClass));
		sb.append(")");
		return sb.toString();
	}

	private static boolean isRelevant(Method method) {
		return !method.isSynthetic() && !method.isBridge();
	}

	private static <V, T extends Throwable> V getProperty(ValueCache<Class<?>, Map<String, V>, T> cache,
	    MethodReferenceEntry methodReferenceEntry, V defaultValue) throws T {
		Map<String, V> map = cache.computeIfAbsent(
		                       methodReferenceEntry.getOwnerClassModel().getClassWeAreModelling());
		String key = toSignature(methodReferenceEntry);
		if (map.containsKey(key))
			return map.get(key);
		return defaultValue;
	}

	private static String toSignature(MethodReferenceEntry methodReferenceEntry) {
		NameAndTypeEntry nameAndTypeEntry = methodReferenceEntry.getNameAndTypeEntry();
		return nameAndTypeEntry.getNameUTF8Entry().getUTF8() +
		       nameAndTypeEntry.getDescriptorUTF8Entry().getUTF8();
	}

	private static final ValueCache<Class<?>, Map<String, String>, RuntimeException>
	mappedMethodNamesCache = cacheProperty(new ValueComputer<Class<?>, Map<String, String>>() {
		@Override
		public Map<String, String> compute(Class<?> key) {
			Map<String, String> properties = new HashMap<>();
			for (final Method method : key.getDeclaredMethods()) {
				if (isRelevant(method) && method.isAnnotationPresent(OpenCLMapping.class)) {
					// ultimately, need a way to constrain this based upon signature (to disambiguate abs(float) from abs(int);
					final OpenCLMapping annotation = method.getAnnotation(OpenCLMapping.class);
					final String mapTo = annotation.mapTo();
					if (mapTo != null && !mapTo.equals(""))
						properties.put(toSignature(method), mapTo);
				}
			}
			return properties;
		}
	});

	private static <K, V, T extends Throwable> ValueCache<Class<?>, Map<K, V>, T> cacheProperty(
	  final ThrowingValueComputer<Class<?>, Map<K, V>, T> throwingValueComputer) {
		return ValueCache.on(new ThrowingValueComputer<Class<?>, Map<K, V>, T>() {
			@Override
			public Map<K, V> compute(Class<?> key) throws T {
				Map<K, V> properties = new HashMap<>();
				Deque<Class<?>> superclasses = new ArrayDeque<>();
				Class<?> currentSuperClass = key;
				do {
					superclasses.push(currentSuperClass);
					currentSuperClass = currentSuperClass.getSuperclass();
				} while (currentSuperClass != Object.class);
				for (Class<?> clazz : superclasses) {
					// Overwrite property values for shadowed/overriden methods
					properties.putAll(throwingValueComputer.compute(clazz));
				}
				return properties;
			}
		});
	}

	public static void invalidateCaches() {
		atomic32Cache.invalidate();
		atomic64Cache.invalidate();
		mappedMethodFlags.invalidate();
		mappedMethodNamesCache.invalidate();
		openCLDelegateMethodFlags.invalidate();
	}
}
