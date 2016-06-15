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

import scala.math.random

import org.apache.spark._

import org.apache.spark.blaze._
import org.apache.j2fa.Annotation._

object VecAdd {
  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("Vector Add")
    val sc = new SparkContext(conf)
    val acc = new BlazeRuntime(sc)
    val n = args(0).toInt
    val npartition = args(1).toInt
    val vec = sc.parallelize(0 until n).map(e => {
      (e, e + 1)
    }).repartition(npartition)

    val a_vec = acc.wrap(vec)

    val first = a_vec.map_acc(new VecAdd).first
    println("First element: " + first)

    acc.stop()
  }
}

class VecAdd extends Accelerator[Tuple2[Int, Int], Int] {

  val id: String = "VecAdd"

  def getArgNum = 0

  def getArg(idx: Int) = None

  @J2FA_Kernel
  override def call(in: Tuple2[Int, Int]): Int = {
    in._1 + in._2
  }
}
