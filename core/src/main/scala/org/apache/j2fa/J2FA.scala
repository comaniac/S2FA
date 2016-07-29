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
import scala.io._
import scala.sys.process._

import java.io._
import java.net._
import java.util.logging.Logger
import java.util.logging.Level
import java.util.LinkedList
import java.util.jar.JarFile

import org.apache.j2fa.AST._
import com.amd.aparapi.Config

object J2FA {
  val logger = Logger.getLogger(Config.getLoggerName)
 
  def main(args : Array[String]) {
    if (args.length < 5) {
      System.err.print("Usage: J2FA <Source file> <jar paths> ")
      System.err.println("<Load level> <Accelerator class name> <Output kernel file>")
      System.exit(1)
    }
    logger.info("J2FA -- Java to FPGA Accelerator Framework")

    // Parse source code to identify kernel methods
    val srcTree = ASTUtils.getSourceTree(args(0))
    val kernels = ASTUtils.getKernelInfo(srcTree)

    if (logger.isLoggable(Level.FINE)) {
      logger.fine("Type environment:")
      kernels.getVariables.foreach({
        case (name, vtype) =>
          println(name + ": " + vtype)
      })
    }

    // Load classes
    val jarPaths = args(1).split(":")
    var jars = List[URL]()
    jarPaths.foreach(path => {
      val file = new File(path).toURI.toURL
      jars = jars :+ file
    })

    val loader = new URLClassLoader(jars.toArray)
    val loadLevel = args(2).toInt
    var lastPos = 0
    for (i <- 0 until loadLevel) {
      if (args(3).indexOf(".", lastPos + 1) != -1)
        lastPos = args(3).indexOf(".", lastPos + 1)
    }
    val pkgPrefix = args(3).substring(0, lastPos).replace('.', '/')
    logger.info("Loading classes from package " + pkgPrefix)

    jarPaths.foreach({ path =>
      val jarFile = new JarFile(path)
      var entity = jarFile.entries
      while (entity.hasMoreElements) {
        val je = entity.nextElement
        if (!je.isDirectory && je.getName.endsWith(".class") && je.getName.startsWith(pkgPrefix)) {
          val clazzName = je.getName.substring(0, je.getName.length - 6).replace('/', '.')
          logger.finest("Load class " + clazzName)
          try {
            loader.loadClass(clazzName)
          } catch {
            case _ : Throwable =>
              logger.finest("Cannot find class " + clazzName + " in provided packages")
          }
        }
      }
    })

    logger.info("Loading target class: " + args(3))
    val clazz = loader.loadClass(args(3))

    // Compile each kernel method to accelerator kernel
    kernels.getMethods.foreach({
      case (mName, mInfo) =>
        logger.info("Compiling kernel " + mInfo.toString)
        val kernel = new Kernel(kernels.getVariables, clazz, mInfo, loader)
        if (kernel.generate == true) {
          val outPath = args(4).substring(0, args(4).lastIndexOf("/") + 1)
          val kernelString = kernel.getKernel
          val headerString = kernel.getHeader
          val kernelFile = new PrintWriter(new File(args(4)))
          kernelFile.write(kernelString)
          kernelFile.close
          val headerFile = new PrintWriter(new File(outPath + "j2fa_class.h"))
          headerFile.write(headerString)
          headerFile.close
        }
    })
  }
}

