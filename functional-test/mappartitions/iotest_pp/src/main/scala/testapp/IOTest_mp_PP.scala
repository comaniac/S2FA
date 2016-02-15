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

class IOTest_PP
  extends Accelerator[Double, Double] {

  val id: String = "IOTest_PP"

  def getArgNum(): Int = 0

  def getArg(idx: Int): Option[_] = idx match {
    case _ => None
  }

  @J2FA_Kernel(kernel_type="mapPartitions")
  override def call(in: Iterator[Double]): Iterator[Double] = {
    var count: Double = 0.0
    val out = new Array[Double](1)

    while (in.hasNext) {
      count += in.next
    }
    out(0) = count
    out.iterator
  }
}

object TestApp {
    def main(args : Array[String]) {
      val sc = get_spark_context("IOTest_PP")
      val data = new Array[Double](1024).map(e => random)
      val rdd = sc.parallelize(data, 8).cache

      val acc = new BlazeRuntime(sc)
      val rdd_acc = acc.wrap(rdd)

      println("map Result: " + rdd_acc.map_acc(new IOTest_PP).reduce((a, b) => (a + b)))
      println("CPU Result: " + rdd_acc.reduce((a, b) => (a + b)))

      acc.stop()
    }

    def get_spark_context(appName : String) : SparkContext = {
        val conf = new SparkConf()
        conf.setAppName(appName)
        
        return new SparkContext(conf)
    }
}

