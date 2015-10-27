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

class ArrayTest(v: BlazeBroadcast[Array[Double]]) 
  extends Accelerator[Array[Double], Array[Double]] {

  val id: String = "ArrayTest"

  def getArgNum(): Int = 1

  def getArg(idx: Int): Option[_] = {
    if (idx == 0)
      Some(v)
    else
      None
  }

  override def call(in: Array[Double]): Array[Double] = {
    val ary = new Array[Double](15)
    val s = v.value
    var i = 0
    while (i < 15) {
      ary(i) = in(i) + s(0)
      i += 1
    }
    ary
  }

  override def call(in: Iterator[Array[Double]]): Iterator[Array[Double]] = {
    val s = v.value
    val out = new Array[Array[Double]](1)
    val ary = new Array[Double](15)

    while (in.hasNext) {
      val data = in.next
      var i = 0
      while (i < 15) {
        ary(i) = data(i) + s(0)
        i += 1
      }
    }
    //out(0) = ary
    out.iterator
  }
}

object TestApp {
    def main(args : Array[String]) {
      val sc = get_spark_context("Test App")
      val acc = new BlazeRuntime(sc)

      val v = Array(1.1, 2.2, 3.3)

      println("Functional test: array type AccRDD with array type broadcast value")
      val data = new Array[Array[Double]](20000)
      for (i <- 0 until 20000) {
        data(i) = new Array[Double](15).map(e => random)
      }
      val rdd = sc.parallelize(data, 10)
      val rdd_acc = acc.wrap(rdd)    
      val brdcst_v = acc.wrap(sc.broadcast(v))
      val rdd2 = rdd_acc.map_acc(new ArrayTest(brdcst_v))
      val res0 = rdd2.collect
//      println("Map result: " + res0(0)(0))
//      val res1 = rdd_acc.mapPartitions_acc(new ArrayTest(brdcst_v)).collect
//      println("MapPartitions result: " + res1(0)(0))
//      val res2 = rdd.map(e => e.map(ee => ee + v(0))).collect
//      println("CPU result: " + res2(0)(0))

      acc.stop()
    }

    def get_spark_context(appName : String) : SparkContext = {
        val conf = new SparkConf()
        conf.setAppName(appName)
        
        return new SparkContext(conf)
    }
}

