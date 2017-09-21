/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.j2fa

import ModeledType._

import scala.reflect.ClassTag
import scala.io._
import scala.sys.process._
import scala.collection.mutable.Map
import collection.JavaConverters._

import java.lang.reflect.Method
import java.util._
import java.util.logging.Logger
import java.io._
import java.net._
import java.util.LinkedList

import com.amd.aparapi.Config
import com.amd.aparapi.internal.model.Entrypoint
import com.amd.aparapi.internal.model.ClassModel
import com.amd.aparapi.internal.writer.BlockWriter._
import com.amd.aparapi.internal.writer._
import com.amd.aparapi.internal.writer.JParameter
import com.amd.aparapi.internal.writer.JParameter.DIRECTION
import com.amd.aparapi.internal.model.CustomizedClassModels.CustomizedClassModelMatcher
import com.amd.aparapi.internal.writer.KernelWriter
import com.amd.aparapi.internal.writer.KernelWriter.WriterAndKernel
import com.amd.aparapi.internal.util.{Utils => AparapiUtils}

import org.apache.j2fa.AST._

class Kernel(kernelSig: String, ioInfo: ((String, Int), (String, Int))) {

  def this(sig: String) = this(sig, (("", 1), ("", 1)))

  val logger = Logger.getLogger(Config.getLoggerName)

  val params : List[JParameter] = new LinkedList[JParameter]
  def getArgs = params

  var refParams: List[JParameter] = new LinkedList[JParameter]
  def getRefArgs = refParams

  var kernelString: String = ""
  var headerString: String = ""
  def getKernel = kernelString
  def getHeader = headerString

  val kernelName = Utils.getLegalKernelName(kernelSig)
  val kernelType = kernelSig.substring(0, kernelSig.indexOf('('))
  val lambdaClassName = kernelSig.substring(kernelSig.indexOf('(') + 1, kernelSig.indexOf(')'))
  def getKernelName = kernelName
  def getKernelType = kernelType

  def generateWithLambdaExp: Boolean = {
    System.setProperty("com.amd.aparapi.kernelType", kernelType)

    try {
      val clazz = getClass.getClassLoader.loadClass(lambdaClassName)
      val classModel : ClassModel = ClassModel.createClassModel(clazz, null, 
        new CustomizedClassModelMatcher(null))
      
      // Identify the apply method in the lambda class
      val methods = clazz.getMethods
      var applyMethod: Method = null
      methods.foreach(m => {
        if (!m.isBridge() && m.getName.equals("apply")) {
          if (applyMethod != null)
            logger.warning("Found multiple apply methods and only take the first one")
          else
            applyMethod = m
        }
      })
      if (applyMethod == null) {
        logger.severe("Cannot find the apply method in " + lambdaClassName)
        System.exit(1)
      }

      // Setup arguments and return values
      // NOTE: Currently we assume only one input argument 
      // (but we can have multiple reference arguments, of course)
      var sig = "("
      require(applyMethod.getParameterTypes.length == 1)
      val inType = AparapiUtils.convertToBytecodeType(if ("" == ioInfo._1._1) {
        // Infer type info from bytecode
        applyMethod.getParameterTypes()(0).getName
      }
      else {
        // Fetch type info from config
        ioInfo._1._1
      })

      // FIXME: Since we cannot get the variable name of the
      // input, we fix it as "in". This won't be a problem for
      // primitive types but objects with type paramters (e.g. Tuple2),
      // because the code generator will try to use the local variable
      // table to find the type hint. Mismatched variable name would cause
      // type hint missing. Thus, the input must be named "in" if it is
      // a class with type parameters.
      val param = JParameter.createParameter(
        inType, "in", JParameter.DIRECTION.IN)
      param.setItemLength(ioInfo._1._2)
      params.add(param)
      sig += (if (inType.contains("<")) inType.substring(0, inType.indexOf("<")) + ";"
              else inType)

      val retType = AparapiUtils.convertToBytecodeType(if ("" == ioInfo._2._1) {
        applyMethod.getReturnType.getName
      }
      else {
        ioInfo._2._1
      })
      if (!retType.equals("void")) {
        val param = JParameter.createParameter(
            retType, "s2fa_out", JParameter.DIRECTION.OUT)
        param.setItemLength(ioInfo._2._2)
        params.add(param)
      }
      sig += ")" + (if (retType.contains("<")) retType.substring(0, retType.indexOf("<")) + ";"
                    else retType)
      logger.info("Kernel signature: " + sig)

      // Create Entrypoint and generate the kernel
      val entryPoint = classModel.getEntrypoint("apply", sig, applyMethod, params)
      val writerAndKernel = KernelWriter.writeToString(kernelName, entryPoint, params)
      refParams = KernelWriter.getRefArgs(entryPoint)
      var genString = writerAndKernel.kernel
      genString = KernelWriter.postProcforHLS(genString)
      headerString = genString.substring(0, genString.indexOf("// Kernel source code starts here\n"))
      kernelString = genString.substring(genString.indexOf("// Kernel source code starts here\n") + 34)
      true
    } catch {
      case e: java.lang.ClassNotFoundException =>
        logger.severe("Cannot load class " + lambdaClassName + ", make sure " + 
          "the -classpath covers all necessary files.")
        false
      case e: Throwable =>
        val sw = new StringWriter
        e.printStackTrace(new PrintWriter(sw))
        val fullMsg = sw.toString
        logger.severe("Kernel generated failed: " + fullMsg)
        false
    }
  }

  def generate: Boolean = {
    if (lambdaClassName.length == 0) {
      logger.finest(kernelSig + " doesn't have lambda expression")
      true
    }
    else
      generateWithLambdaExp
  }

  def getArgString: String = {
    require(kernelString != "", "Cannot invoke this method before generating kernel!")
    val argStart = kernelString.indexOf(kernelName + "(") + kernelName.length + 1
    val argEnd = kernelString.indexOf(")", kernelString.indexOf(kernelName + "(") + kernelName.length + 1)
    kernelString.substring(argStart, argEnd)
  }
}

