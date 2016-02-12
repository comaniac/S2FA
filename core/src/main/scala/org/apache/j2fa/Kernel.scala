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
import org.apache.j2fa.AST._

class Kernel(id: String, accClazz: Class[_], mInfo: MethodInfo) {

  def generate = {

    System.setProperty("com.amd.aparapi.logLevel", "INFO")
    System.setProperty("com.amd.aparapi.enable.NEW", "true")
    System.setProperty("com.amd.aparapi.enable.INVOKEINTERFACE", "true")

    val mName = mInfo.getName
    var kernelPath : String = "/tmp/j2fa_kernel_" + id + "_" + mName
    val outputMerlin = if (mInfo.getConfig("output_format") == "Merlin") true else false
    var isMapPartitions: Boolean = if (mInfo.getConfig("kernel_type") == "mapPartitions") true else false

    Logging.info("Kernel type: " + mInfo.getConfig("kernel_type"))
    Logging.info("Output format: " + mInfo.getConfig("output_format"))

    if (outputMerlin) {
      System.setProperty("com.amd.aparapi.enable.MERLINE", "true")
      kernelPath = kernelPath + ".c" 
    }
    else
      kernelPath = kernelPath + ".cl"

    val classModel : ClassModel = ClassModel.createClassModel(accClazz, null, new ShouldNotCallMatcher())

    // FIXME: Find method using name and sig.
    val method =  if (!isMapPartitions) classModel.getPrimitiveCallMethod 
                  else classModel.getPrimitiveCallPartitionsMethod

    try {
      if (method == null)
        throw new RuntimeException("Cannot find available kernel method from bytecode.")
      val descriptor : String = method.getDescriptor
      val params : LinkedList[ScalaParameter] = new LinkedList[ScalaParameter]

      val methods = accClazz.getMethods
      var fun: Object = null
      methods.foreach(m => {
        val des = m.toString
        if (!isMapPartitions && des.contains(mName) && !des.contains("Object") && !des.contains("Iterator"))
          fun = m
        else if (isMapPartitions && des.contains(mName) && !des.contains("Object") && des.contains("Iterator"))
          fun = m
      })
      val entryPoint = classModel.getEntrypoint(mName, descriptor, fun, params, null)
      val writerAndKernel = KernelWriter.writeToString(entryPoint, params)
      val openCL = writerAndKernel.kernel
      val kernelFile = new PrintWriter(new File(kernelPath))
      kernelFile.write(KernelWriter.applyXilinxPatch(openCL))
      kernelFile.close
      //val res = applyBoko(kernelPath)
      Logging.info("Generate and optimize the kernel successfully")
      //logWarning += "[Boko] " + res + "\n"
      true
    } catch {
      case e: Throwable =>
        val sw = new StringWriter
        e.printStackTrace(new PrintWriter(sw))
        val fullMsg = sw.toString
        Logging.severe("Kernel generated failed: " + fullMsg)
        false
    }
  } 
}
