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

class IOSuite extends J2FAFunSuite {

  test("Map IO Test: (Merlin) Primitive/Primitive") {
    val jarFileURL = getClass.getResource("/map_iotest_pp-0.0.0.jar")
    val className = "IOTest_PP"
    assert(checkResult(runTest(jarFileURL, className), 1))
  }

  test("Map IO Test: (Merlin) Tuple2/Primitive") {
    val jarFileURL = getClass.getResource("/map_iotest_tp-0.0.0.jar")
    val className = "IOTest_TP"
    assert(checkResult(runTest(jarFileURL, className), 1))
  }

   test("Map IO Test: (Merlin) Broadcast") {
    val jarFileURL = getClass.getResource("/map_iotest_b-0.0.0.jar")
    val className = "IOTest_B"
    assert(checkResult(runTest(jarFileURL, className), 1))
  }
 
  test("Map IO Test: (Merlin) Array[Primitive]/Array[Primitive]") {
    val jarFileURL = getClass.getResource("/map_iotest_apap-0.0.0.jar")
    val className = "IOTest_APAP"
    assert(checkResult(runTest(jarFileURL, className), 1))
  }

//  test("MapPartitions IO Test: (Merlin) Primitive/Primitive") {
//    val jarFileURL = getClass.getResource("/mappartitions_iotest_pp-0.0.0.jar")
//    val className = "IOTest_PP"
//    assert(checkResult(runTest(jarFileURL, className), 1))
//  }
}
