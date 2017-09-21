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

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.io._
import scala.sys.process._
import scala.xml.XML
import scala.collection.JavaConversions.asScalaBuffer

import java.io._
import java.net._
import java.util.logging.Logger
import java.util.logging.Level
import java.util.LinkedList
import java.util.jar.JarFile

import org.apache.j2fa.Annotation._
import org.apache.j2fa.AST._
import com.amd.aparapi.Config
import com.amd.aparapi.internal.model.ClassModel
import com.amd.aparapi.internal.model.CustomizedClassModels.CustomizedClassModelMatcher

object J2FA {
  val logger = Logger.getLogger(Config.getLoggerName)
 
  def main(args : Array[String]) {
    if (args.length < 2) {
      System.err.print("Usage: J2FA <Config file> <Output kernel path>")
      System.exit(1)
    }
    logger.info("J2FA -- Java to FPGA Accelerator Framework")
    
    // Create customized class loader
    // val jarPaths = args(0).split(":")
    // var jars = List[URL]()
    // jarPaths.foreach(path => {
    //   val file = new File(path).toURI.toURL
    //   jars = jars :+ file
    // })
    // val loader = new URLClassLoader(jars.toArray)
    
    // var lastPos = 0
    // for (i <- 0 until loadLevel) {
    //   if (args(3).indexOf(".", lastPos + 1) != -1)
    //     lastPos = args(3).indexOf(".", lastPos + 1)
    // }
    // val pkgPrefix = args(3).substring(0, lastPos).replace('.', '/')
    // logger.info("Loading classes from package " + pkgPrefix)

    // jarPaths.foreach({ path =>
    //   val jarFile = new JarFile(path)
    //   var entity = jarFile.entries
    //   while (entity.hasMoreElements) {
    //     val je = entity.nextElement
    //     if (!je.isDirectory && je.getName.endsWith(".class") && je.getName.startsWith(pkgPrefix)) {
    //       val clazzName = je.getName.substring(0, je.getName.length - 6).replace('/', '.')
    //       logger.finest("Load class " + clazzName)
    //       try {
    //         loader.loadClass(clazzName)
    //       } catch {
    //         case _ : Throwable =>
    //           logger.finest("Cannot find class " + clazzName + " in provided packages")
    //       }
    //     }
    //   }
    // })

    // Parse config file
    val config = XML.loadFile(args(0))

    try {
      val targetClazz = (config \ "main" \ "class").text
      val targetMethod = (config \ "main" \ "method").text
      val targetVar = (config \ "main" \ "variable").text

      logger.info("Loading target class: " + targetClazz)      
      
      var kernelList = List[Kernel]()
      val outPath = args(1).substring(0, args(1).lastIndexOf("/") + 1)

      // Load the target class
      val clazz = getClass().getClassLoader().loadClass(targetClazz)
      
      // Compile each kernel method to accelerator design
      clazz.getDeclaredMethods.foreach({m =>
        if (m.getName.equals(targetMethod)) {
          logger.info("Kernel variable " + targetVar + " in " + m.getName)

          // Build IO info table
          val ioInfos = (config \ "kernel").map(e => {
            val fun = (e \ "function").text
            val inItemLength = (e \ "input_item_length").text.toInt
            val inType = (e \ "input_type").text
            val outItemLength = (e \ "output_item_length").text.toInt
            val outType = (e \ "output_type").text
            (fun, (inType, inItemLength), (outType, outItemLength))
          })

          // Setup output format (currently only use MerlinC)
          System.setProperty("com.amd.aparapi.enable.MERLIN", "true")

          // Create Aparapi class model
          val classModel : ClassModel = ClassModel.createClassModel(clazz, null, 
            new CustomizedClassModelMatcher(null))
          
          val methodCallsJava = classModel.getAllMethodCallsByVar(m.getName(), Utils.getMethodSignature(m), targetVar)
          val methodCalls = asScalaBuffer(methodCallsJava)

          logger.fine("Kernel flow:")
          methodCalls.foreach(call => logger.fine("  -> " + call))

          // Compile each kernel method to accelerator kernel
          methodCalls.foreach(call => {
            val ioInfo = ioInfos.find(e => e._1 == call.toString)
            
            val kernel = if (ioInfo.isDefined) {
              logger.info("Compiling kernel " + call + ", IO={" +
                ioInfo.get._2 + ", " + ioInfo.get._3 + "}")
              Some(new Kernel(call, (ioInfo.get._2, ioInfo.get._3)))
            } else {
              None
            }

            if (kernel.isDefined) {
              kernelList = kernel.get :: kernelList
              if (kernel.get.generate == true)
                logger.info("Successfully generated the kernel " + call)
              else
                throw new RuntimeException("Fail to generate the kernel " + call)
            }
          })
        }
      })

      // Generate the top function
      kernelList = kernelList.reverse
      val topKernelWriter = new TopKernelWriter(kernelList)      
      val pwKernel = new PrintWriter(new File(outPath + "/kernel_top.cpp"))
      pwKernel.write(topKernelWriter.writeToString)
      pwKernel.close
      val pwHeader = new PrintWriter(new File(outPath + "/kernel_header.h"))
      pwHeader.write(topKernelWriter.writeHeaderToString)
      pwHeader.close
    } catch {
      case e: java.lang.ClassNotFoundException =>
        logger.severe("Cannot load class " + args(0) + ", make sure " + 
          "the -classpath covers all necessary files.")
        System.exit(1)
      case e: Throwable =>
        val sw = new StringWriter
        e.printStackTrace(new PrintWriter(sw))
        val fullMsg = sw.toString
        logger.severe(fullMsg)
        System.exit(1)
    }
  }
}

