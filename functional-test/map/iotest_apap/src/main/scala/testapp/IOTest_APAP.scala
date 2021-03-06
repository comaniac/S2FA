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

class IOTest_APAP(D: Int) 
  extends Accelerator[Array[Double], Array[Double]] {

  val id: String = "IOTest_APAP"

  def getArgNum(): Int = 1

  def getArg(idx: Int): Option[_] = idx match {
    case 0 => Some(D)
    case _ => None
  }

  @J2FA_Kernel
  override def call(in: Array[Double]): Array[Double] = {
    val out = new Array[Double](16)
    var i = 0
    while (i < D) {
      out(i) = in(i)
      i += 1
    }
    out
  }
}

object TestApp {
    def main(args : Array[String]) {
      val sc = get_spark_context("Test App")
      val acc = new BlazeRuntime(sc)
      val dim = 8

      val data = new Array[Array[Double]](16)
      for (i <- 0 until 16) {
        data(i) = new Array[Double](dim).map(e => random)
      }
      val rdd = sc.parallelize(data, 4)

      val rdd_acc = acc.wrap(rdd)    

      val res0 = rdd_acc.map_acc(new IOTest_APAP(dim)).collect
      val res1 = rdd.collect

      // compare results
      if (res0.deep != res1.deep)
      {
        println("input: \n" + data.deep.mkString("\n"))
        println("map result: \n" + res1.deep.mkString("\n"))
        println("map_acc results: \n" + res0.deep.mkString("\n"))
      }
      else {
        println("result correct")
      }
      acc.stop()
    }

    def get_spark_context(appName : String) : SparkContext = {
        val conf = new SparkConf()
        conf.setAppName(appName)
        
        return new SparkContext(conf)
    }
}

