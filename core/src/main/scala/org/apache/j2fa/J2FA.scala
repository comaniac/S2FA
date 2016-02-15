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

import org.apache.spark.blaze.Accelerator
import org.apache.j2fa.AST._

object J2FA {

  def main(args : Array[String]) {
    if (args.length < 4) {
      System.err.println("Usage: J2FA <Source file> <jar path> <Accelerator class name> <Output kernel file>")
      System.exit(1)
    }
    Logging.info("J2FA -- Java to FPGA Accelerator Framework")

    val srcTree = ASTUtils.getSourceTree(args(0))
    val kernelMethods = ASTUtils.getKernelMethods(srcTree)

    // FIXME: User input classpath
    val jars = Array((new File(args(1)).toURI.toURL),
                      new URL("file://" + sys.env("BLAZE_HOME") + "/accrdd/target/blaze-1.0-SNAPSHOT.jar"))
    val loader = new URLClassLoader(jars)
    val clazz = loader.loadClass(args(2))

    kernelMethods.foreach({
      case (mName, mInfo) =>
        Logging.info("Compiling kernel " + mInfo.toString)
        val kernel = new Kernel(clazz, mInfo)
        val kernelString = kernel.generate
        if (kernelString.isEmpty == false) {
          val kernelFile = new PrintWriter(new File(args(3)))
          kernelFile.write(kernelString.get)
          kernelFile.close
        }

      case _ =>
    })
  }
}

