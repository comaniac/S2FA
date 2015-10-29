package org.apache.spark.blaze

import ModeledType._

import scala.reflect.ClassTag
import scala.io._
import scala.sys.process._

import java.io._
import java.util.LinkedList

import com.amd.aparapi.internal.model.Entrypoint
import com.amd.aparapi.internal.model.ClassModel
import com.amd.aparapi.internal.writer.BlockWriter._
import com.amd.aparapi.internal.writer._
import com.amd.aparapi.internal.writer.ScalaParameter.DIRECTION
import com.amd.aparapi.internal.model.HardCodedClassModels.ShouldNotCallMatcher
import com.amd.aparapi.internal.writer.KernelWriter
import com.amd.aparapi.internal.writer.KernelWriter.WriterAndKernel

object CodeGenUtil {

  def applyBoko(kernelPath : String) = {
    val bokoExe = sys.env("BLAZE_HOME") + "/codegen/boko/bin/Boko"
    val err = new StringBuilder
    val recorder = ProcessLogger((e: String) => err.append(e))

    Process(bokoExe + " " + kernelPath + " -- ") ! recorder
    err.toString
  }

  def genOpenCLKernel[T: ClassTag, U: ClassTag](acc: Accelerator[T, U]) : (String, String) = {
    genOpenCLKernel(acc.id, acc.getClass)
  }

  def genOpenCLKernel(id: String, accClazz: Class[_]) : (String, String) = {
    System.setProperty("com.amd.aparapi.enable.NEW", "true")
    System.setProperty("com.amd.aparapi.enable.INVOKEINTERFACE", "true")

    var logInfo : String = ""
    var logWarning : String = ""

    val methodFunctions = Array("map", "mapPartitions")
    val classModel : ClassModel = ClassModel.createClassModel(accClazz, null, new ShouldNotCallMatcher())

    for (methodFunction <- methodFunctions) {
      val kernelPath : String = "/tmp/blaze_kernel_" + id + "_" + methodFunction + ".cl" 
      // TODO: Should be a shared path e.g. HDFS
      if (new File(kernelPath).exists) {
        logWarning += "Kernel " + methodFunction + " exists, skip generating\n"
      }
      else {
        var isMapPartitions: Boolean = if (methodFunction.contains("Partitions")) true else false
        val method =  if (!isMapPartitions) classModel.getPrimitiveCallMethod 
                      else classModel.getPrimitiveCallPartitionsMethod
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
          val res = applyBoko(kernelPath)
          logInfo += "[CodeGen] Generate and optimize the kernel successfully\n"
          logWarning += "[Boko] " + res + "\n"
        } catch {
          case e: Throwable =>
            val sw = new StringWriter
            e.printStackTrace(new PrintWriter(sw))
            logWarning += "[CodeGen] OpenCL kernel generated failed: " + sw.toString + "\n"
        }
      }
    } // end for 
    (logInfo, logWarning)
  } 
}
