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

import scala.io._
import scala.sys.process._

import java.util._
import java.io._
import java.net._
import java.util.LinkedList

import org.scalatest.{FunSuite, Outcome, Ignore}

import org.apache.j2fa.AST._

abstract class J2FAFunSuite extends FunSuite {

  final protected def runTest(srcFileURL: URL, jarFileURL: URL, className: String) = {
    val srcTree = ASTUtils.getSourceTree(srcFileURL.toString.replace("file:", ""))
    val kernelMethods = ASTUtils.getKernelMethods(srcTree)

    val jars = Array(jarFileURL,
                      new URL("file://" + sys.env("BLAZE_HOME") + "/accrdd/target/blaze-1.0-SNAPSHOT.jar"))
    val loader = new URLClassLoader(jars)
    val clazz = loader.loadClass(className)

    var success = 0
    kernelMethods.foreach({
      case (mName, mInfo) =>
        Logging.info("Compiling kernel " + mInfo.toString)
        val kernel = new Kernel(className, clazz, mInfo)
        success = if (kernel.generate == true) success + 1 else success

      case _ =>
    })

    if (success != kernelMethods.size)
      false
    else
      true
  }

  final protected def checkResult(res: Boolean) = res

  /**
   * Log the suite name and the test name before and after each test.
   *
   * Subclasses should never override this method. If they wish to run
   * custom code before and after each test, they should mix in the
   * {{org.scalatest.BeforeAndAfter}} trait instead.
   */
  final protected override def withFixture(test: NoArgTest): Outcome = {
    val testName = test.text
    val suiteName = this.getClass.getName
    try {
//      println(s"\n\n===== TEST OUTPUT FOR $suiteName: '$testName' =====\n")
      test()
    } finally {
//      println(s"\n\n===== FINISHED $suiteName: '$testName' =====\n")
    }
  }
}

