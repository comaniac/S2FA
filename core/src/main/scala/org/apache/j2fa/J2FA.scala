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

import java.util._
import java.io._
import java.net._
import java.util.LinkedList

import com.amd.aparapi.internal.model.Entrypoint
import com.amd.aparapi.internal.model.ClassModel
import com.amd.aparapi.internal.writer.BlockWriter._
import com.amd.aparapi.internal.writer._
import com.amd.aparapi.internal.writer.ScalaParameter.DIRECTION
import com.amd.aparapi.internal.model.HardCodedClassModels.ShouldNotCallMatcher
import com.amd.aparapi.internal.writer.KernelWriter
import com.amd.aparapi.internal.writer.KernelWriter.WriterAndKernel

import org.apache.spark.blaze.Accelerator

object J2FA {

  def main(args : Array[String]) {
    if (args.length < 2) {
      System.err.println("Usage: J2FA <jar path> <Accelerator class name> <use Merlin?>")
      System.exit(1)
    }
    val jars = Array((new File(args(0)).toURI.toURL),
                      new URL("file://" + sys.env("BLAZE_HOME") + "/accrdd/target/blaze-1.0-SNAPSHOT.jar"))
    val loader = new URLClassLoader(jars)
    val clazz = loader.loadClass(args(1))
    val useMerlin = if (args(2).toLowerCase.equals("y")) true else false

    val codeGenLog = genKernel(args(1), clazz, useMerlin)
    println("CodeGen result: " + codeGenLog._1)
    println("WARNING: " + codeGenLog._2)
  }

   def genKernel[T: ClassTag, U: ClassTag](acc: Accelerator[T, U]) : (String, String) = {
    genKernel(acc.id, acc.getClass, false)
  }

  def genKernel(id: String, accClazz: Class[_], useMerlin: Boolean) : (String, String) = {
    System.setProperty("com.amd.aparapi.logLevel", "FINEST")
    System.setProperty("com.amd.aparapi.enable.NEW", "true")
    System.setProperty("com.amd.aparapi.enable.INVOKEINTERFACE", "true")
    if (useMerlin)
      System.setProperty("com.amd.aparapi.enable.MERLINE", "true")

    var logInfo : String = "\n"
    var logWarning : String = "\n"

    val methodFunctions = Array("map", "mapPartitions")
    val classModel : ClassModel = ClassModel.createClassModel(accClazz, null, new ShouldNotCallMatcher())

    for (methodFunction <- methodFunctions) {
      var kernelPath : String = "/tmp/j2fa_kernel_" + id + "_" + methodFunction
      if (useMerlin)
        kernelPath = kernelPath + ".c" 
      else
        kernelPath = kernelPath + ".cl"

      var isMapPartitions: Boolean = if (methodFunction.contains("Partitions")) true else false
      val method =  if (!isMapPartitions) classModel.getPrimitiveCallMethod 
                    else classModel.getPrimitiveCallPartitionsMethod
      if (isMapPartitions) 
        logInfo += "[MapPartitions] " 
      else 
        logInfo += "[Map] "
      try {
        if (method == null)
          throw new RuntimeException("Cannot find available call method.")
        val descriptor : String = method.getDescriptor
        val params : LinkedList[ScalaParameter] = new LinkedList[ScalaParameter]

        val methods = accClazz.getMethods
        var fun: Object = null
        methods.foreach(m => {
          val des = m.toString
          if (!isMapPartitions && des.contains("call") && !des.contains("Object") && !des.contains("Iterator"))
            fun = m
          else if (isMapPartitions && des.contains("call") && !des.contains("Object") && des.contains("Iterator"))
            fun = m
        })
        val entryPoint = classModel.getEntrypoint("call", descriptor, fun, params, null)
        val writerAndKernel = KernelWriter.writeToString(entryPoint, params)
        val openCL = writerAndKernel.kernel
        val kernelFile = new PrintWriter(new File(kernelPath))
        kernelFile.write(KernelWriter.applyXilinxPatch(openCL))
        kernelFile.close
        //val res = applyBoko(kernelPath)
        logInfo += "Generate and optimize the kernel successfully\n"
        //logWarning += "[Boko] " + res + "\n"
      } catch {
        case e: Throwable =>
          val sw = new StringWriter
          e.printStackTrace(new PrintWriter(sw))
          val fullMsg = sw.toString
//          logInfo += "Kernel generated failed: " + fullMsg.substring(0, fullMsg.indexOf("\n")) + "\n"
          logInfo += "Kernel generated failed: " + fullMsg + "\n"
      }
    } // end for 
    (logInfo, logWarning)
  } 
 
}

