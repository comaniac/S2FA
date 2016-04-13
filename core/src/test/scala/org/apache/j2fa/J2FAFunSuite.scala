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

import java.io._
import java.net._
import java.util.logging.Logger
import java.util.LinkedList
import java.util.jar.JarFile

import org.scalatest.{FunSuite, Outcome, Ignore}

import com.amd.aparapi.Config
import org.apache.j2fa.AST._

abstract class J2FAFunSuite extends FunSuite {
  val logger = Logger.getLogger(Config.getLoggerName)

  final protected def runTest(srcFileURL: URL, jarFileURL: URL, className: String) = {
    val srcFile = srcFileURL.toString.replace("file:", "")
    val jarPath = jarFileURL.toString.replace("file:", "") + ":" + 
          sys.env("BLAZE_HOME") + "/accrdd/target/blaze-1.0.jar"
    val level = 4
    val fileName = "/tmp/j2fa_" + className + ".c"
    val args = Array(srcFile, jarPath, level.toString, className, fileName)

    J2FA.main(args)

    val plog = ProcessLogger((e: String) => println("Error: " + e))
    val code = "gcc -std=c99 -c " + fileName + " -o /dev/null" ! plog

    if (code == 0)
      true
    else
      false
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
      test()
    } finally {
    }
  }
}

