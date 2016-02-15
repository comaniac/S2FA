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

import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import org.apache.spark.util.random._
import Array._
import scala.math._
import org.apache.spark.rdd._
import java.net._

// comaniac: Import extended package
import org.apache.spark.blaze._
import org.apache.j2fa.Annotation._

class IOTest_B(b_1: Double, b_2: BlazeBroadcast[Array[Double]])
  extends Accelerator[Double, Double] {

  val id: String = "IOTest_B"

  def getArgNum(): Int = 2

  def getArg(idx: Int): Option[_] = idx match {
    case 0 => Some(b_1)
    case 1 => Some(b_2)
    case _ => None
  }

  @J2FA_Kernel
  override def call(in: Double): Double = {
    val b = b_2.value
    in + b_1 + b(0)
  }
}

object TestApp {
    def main(args : Array[String]) {
      val sc = get_spark_context("IOTest_B")
      val data = new Array[Double](1024).map(e => random)
      val rdd = sc.parallelize(data, 8).cache

      val acc = new BlazeRuntime(sc)
      val rdd_acc = acc.wrap(rdd)

      val v_1 = 10.1
      val v_2 = acc.wrap(sc.broadcast(Array(2.5)))

      println("map Result: " + rdd_acc.map_acc(new IOTest_B(v_1, v_2)).reduce((a, b) => (a + b)))
      println("CPU Result: " + rdd_acc.reduce((a, b) => (a + b + 12.6)))

      acc.stop()
    }

    def get_spark_context(appName : String) : SparkContext = {
        val conf = new SparkConf()
        conf.setAppName(appName)
        
        return new SparkContext(conf)
    }
}

