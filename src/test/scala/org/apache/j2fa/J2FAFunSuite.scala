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

import org.scalatest.{FunSuite, Outcome}

abstract class J2FAFunSuite extends FunSuite {

  final protected def runTest(jarFileURL: URL, className: String) = {
    val jars = Array(jarFileURL,
                     new URL("file://" + sys.env("BLAZE_HOME") + "/accrdd/target/blaze-1.0-SNAPSHOT.jar"))
    val loader = new URLClassLoader(jars)
    val clazz = loader.loadClass(className)
    val useMerlin = true

    val codeGenLog = J2FA.genKernel(className, clazz, useMerlin)
    codeGenLog._1
  }

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
      println(s"\n\n===== TEST OUTPUT FOR $suiteName: '$testName' =====\n")
      test()
    } finally {
      println(s"\n\n===== FINISHED $suiteName: '$testName' =====\n")
    }
  }
}

