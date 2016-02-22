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

class Point(var x: Double, var y: Double, var z: Double) 
    extends java.io.Serializable {
  
  def dis(p: Point) = {
    val d = sqrt(
            (x - p.x) * (x - p.x) + 
            (y - p.y) * (y - p.y) +
            (z - p.z) * (y - p.z))
    d
  }

  override def toString = {
    val str = x + ", " + y + ", " + z
    str
  }
}

class IOTest_Obj(b_1: Point)
  extends Accelerator[Point, Point] {

  val id: String = "IOTest_Obj"

  def getArgNum(): Int = 2

  def getArg(idx: Int): Option[_] = idx match {
    case 0 => Some(b_1)
    case _ => None
  }

  @J2FA_Kernel
  override def call(in: Point): Point = {
    val refPoint = b_1
    val d = in.dis(refPoint)
    refPoint.x = in.x + d
    refPoint.y = in.y + d
    refPoint.z = in.z + d

    new Point(refPoint.x, refPoint.y, refPoint.z)
  }
}

object TestApp {
    def main(args : Array[String]) {
      val sc = get_spark_context("IOTest_Obj")
      val data = new Array[Point](1024).map(e => new Point(random, random, random))
      val rdd = sc.parallelize(data, 8).cache

      val acc = new BlazeRuntime(sc)
      val rdd_acc = acc.wrap(rdd)

      val v_1 = new Point(random, random, random)

      val result1 = rdd_acc.map_acc(new IOTest_Obj(v_1)).collect
      val result2 = rdd.map(e => {
        val refPoint = v_1
        val d = e.dis(refPoint)
        refPoint.x = e.x + d
        refPoint.y = e.y + d
        refPoint.z = e.z + d

        new Point(refPoint.x, refPoint.y, refPoint.z)
      }).collect
      println("map Result (first point): " + result1(0))
      println("CPU Result (first point): " + result2(0))

      acc.stop()
    }

    def get_spark_context(appName : String) : SparkContext = {
        val conf = new SparkConf()
        conf.setAppName(appName)
        
        return new SparkContext(conf)
    }
}

