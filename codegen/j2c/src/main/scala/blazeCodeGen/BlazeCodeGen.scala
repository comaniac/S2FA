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

import java.util._
import java.io.File
import java.net._

// comaniac: Import extended package
import org.apache.spark.blaze._


object BlazeCodeGen {

  def main(args : Array[String]) {
    if (args.length < 2) {
      System.err.println("Usage: BlazeCodeGen <jar path> <Accelerator class name> <use Merlin?>")
      System.exit(1)
    }
    val jars = Array((new File(args(0)).toURI.toURL), 
                     new URL("file://" + sys.env("BLAZE_HOME") + "/accrdd/target/blaze-1.0-SNAPSHOT.jar"))
    val loader = new URLClassLoader(jars)
    val clazz = loader.loadClass(args(1))
    val useMerlin = if (args(2).toLowerCase.equals("y")) true else false

    val codeGenLog = CodeGenUtil.genOpenCLKernel(args(1), clazz, useMerlin)
    println("CodeGen result: " + codeGenLog._1)
    println("WARNING: " + codeGenLog._2)
  }
}

