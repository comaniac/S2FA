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

/** Computes an approximation to pi */
object SparkPi {
  def main(args: Array[String]) {
    val conf = new SparkConf().setAppName("Spark Pi")
    val sc = new SparkContext(conf)
    val acc = new BlazeRuntime(sc)
    val n = args(0).toInt * 100000

    val count = acc.wrap(sc.parallelize(0 until n, 2))
      .mapPartitions_acc(new MonteCarlo)
      .reduce(_ + _)
    println("Pi is roughly " + 4.0 * count / n)

    acc.stop()
  }
}

class MonteCarlo extends Accelerator[Int, Int] {
  val id: String = "MonteCarlo"

  def getArgNum = 0

  def getArg(idx: Int) = None

  override def call(in: Int): Int = {
    var seed0: Int = 13
    var seed1: Int = 17

    val x = myRand(seed0)
    val y = myRand(seed1)
    seed0 = (x * 65535).toInt
    seed1 = (y * 65535).toInt

    if (x * x + y * y < 1.0)
      1
    else
      0
  }

  override def call(in: Iterator[Int]): Iterator[Int] = {
    var count: Int = 0
    val out = new Array[Int](1)

    while (in.hasNext) {
      val p = in.next
      val x = random
      val y = random

      if (x * x + y * y < 1.0)
        count += 1
    }
    out(0) = count
    out.iterator
  }

  def myRand(seed: Int): Float = {
    ((16807 * seed) % 65535).toFloat / 65535
  }
}
