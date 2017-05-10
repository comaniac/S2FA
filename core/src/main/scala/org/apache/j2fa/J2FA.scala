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
      System.err.print("Usage: J2FA <Main class name> <Output kernel path>")
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

    logger.info("Loading target class: " + args(0))
    try {
      var kernelList = List[Kernel]()
      val outPath = args(1).substring(0, args(1).lastIndexOf("/") + 1)

      // Load the target class
      val clazz = getClass().getClassLoader().loadClass(args(0))

      // Compile each kernel method to accelerator design
      clazz.getDeclaredMethods.foreach({m =>
        val annotations = m.getAnnotations
        var kernelAnnot: J2FA_Kernel = null
        annotations.foreach({a => 
          if (a.isInstanceOf[J2FA_Kernel]) {
            kernelAnnot = a.asInstanceOf[J2FA_Kernel]
          }
        })
        if (kernelAnnot != null) {
          val kernelVar = kernelAnnot.kernel()
          logger.info("Kernel variable " + kernelVar + " in " + m.getName)

          // Setup output format (currently only use Merlin C)
          System.setProperty("com.amd.aparapi.enable.MERLIN", "true")

          // Create Aparapi class model
          val classModel : ClassModel = ClassModel.createClassModel(clazz, null, 
            new CustomizedClassModelMatcher(null))
          
          val methodCallsJava = classModel.getAllMethodCallsByVar(m.getName(), Utils.getMethodSignature(m), kernelVar)
          val methodCalls = asScalaBuffer(methodCallsJava)

          logger.fine("Kernel flow:")
          methodCalls.foreach(call => logger.fine("  -> " + call))

          // Compile each kernel method to accelerator kernel
          methodCalls.foreach(call => {
            logger.info("Compiling kernel " + call)
            val kernel = new Kernel(call)
            kernelList = kernel :: kernelList
            if (kernel.generate == true) {
              val fileName = Utils.getLegalKernelName(call)
              val filePath = outPath + fileName

              // Write kernel code
              val kernelString = kernel.getKernel
              val kernelFile = new PrintWriter(new File(filePath + ".cpp"))
              kernelFile.write("#include \"" + fileName + ".h\"\n")              
              kernelFile.write(kernelString)
              kernelFile.close

              // Write header code
              val headerString = kernel.getHeader
              val headerFile = new PrintWriter(new File(filePath + ".h"))
              headerFile.write("#ifndef " + fileName + "\n")
              headerFile.write("#define " + fileName + "\n")
              headerFile.write(headerString)
              headerFile.write("\n#endif\n")
              headerFile.close
              logger.info("Successfully generated the kernel " + call)
            }
            else
              throw new RuntimeException("Fail to generate the kernel " + call)
          })
        }
      })

      // Generate the top function to build the DAG
      kernelList = kernelList.reverse
      val topKernel = new TopKernelWriter(outPath, kernelList)      

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

