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

class IOTest_TP()
  extends Accelerator[Tuple2[Double, Double], Tuple2[Float, Float]] {

  val id: String = "IOTest_TP"

  def getArgNum(): Int = 0

  def getArg(idx: Int): Option[_] = idx match {
    case _ => None
  }

  @J2FA_Kernel
  override def call(in: Tuple2[Double, Double]): Tuple2[Float, Float] = {
    (in._1.toFloat, in._2.toFloat)
  }
}

object TestApp {
    def main(args : Array[String]) {
      val sc = get_spark_context("IOTest_TP")
      val data = new Array[Double](1024).map(e => (random, random))
      val rdd = sc.parallelize(data, 8).cache

      val acc = new BlazeRuntime(sc)
      val rdd_acc = acc.wrap(rdd)

      println("map Result: " + rdd_acc.map_acc(new IOTest_TP).reduce((a, b) => (a._1 + b._1, a._2 + b._2)))
      println("CPU Result: " + rdd_acc.map({case (a, b) => (a.toFloat, b.toFloat)}).reduce((a, b) => (a._1 + b._1, a._2 + b._2)))

      acc.stop()
    }

    def get_spark_context(appName : String) : SparkContext = {
        val conf = new SparkConf()
        conf.setAppName(appName)
        
        return new SparkContext(conf)
    }
}

