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

class Kernel(kernelSig: String) {
  val logger = Logger.getLogger(Config.getLoggerName)

  var kernelString: String = ""
  var headerString: String = ""

  def getKernel = kernelString

  def getHeader = headerString

  def generateWithLambdaExp(kernelName: String, kernelType: String, 
    lambdaClassName: String): Boolean = {
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
      val params : LinkedList[JParameter] = new LinkedList[JParameter]
      var sig = "("
      applyMethod.getParameterTypes.zipWithIndex.foreach(arg => {
        val argClazz = arg._1
        val idx = arg._2
        val param = JParameter.createParameter(
            argClazz.getName, "s2fa_in_" + idx, JParameter.DIRECTION.IN)
        params.add(param)
        sig += Utils.asBytecodeType(argClazz.getName)
      })
      val returnType = applyMethod.getReturnType.getName
      if (!returnType.equals("void")) {
        val param = JParameter.createParameter(
            returnType, "s2fa_out", JParameter.DIRECTION.OUT)
        params.add(param)
      }
      sig += ")" + Utils.asBytecodeType(returnType)
      logger.info("Kernel signature: " + sig)

      // Create Entrypoint and generate the kernel
      val entryPoint = classModel.getEntrypoint("apply", sig, applyMethod, params)
      val writerAndKernel = KernelWriter.writeToString(entryPoint, params)
      var genString = writerAndKernel.kernel
      genString = KernelWriter.applyXilinxPatch(genString)
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
    val kernelName = kernelSig.replace("$", "_").replace("(", "_").replace(")", "_")
    val kernelType = kernelSig.substring(0, kernelSig.indexOf('('))
    val lambdaExpName = kernelSig.substring(kernelSig.indexOf('(') + 1, kernelSig.indexOf(')'))
    if (lambdaExpName.length == 0) {
      logger.finest(kernelSig + " doesn't have lambda expression")
      true
    }
    else
      generateWithLambdaExp(kernelType, kernelType, lambdaExpName)
  } 
}

