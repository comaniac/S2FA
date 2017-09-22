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
import org.apache.spark.SparkContext._
import org.apache.spark.{SparkConf, SparkContext}
import Array._
import org.apache.spark.rdd._

import scala.math._
import java.util._

object LR {

  def main(args : Array[String]) {
    val L = 10
    val D = 784

    val sparkConf = new SparkConf().setAppName("LR")
    val sc = new SparkContext(sparkConf)

    if (args.length < 3) {
      System.err.println("Usage: LR <file> <reps> <iter>")
      System.exit(1)
    }
    val rand = new Random(42)
    val ITERATION = args(2).toInt
    val upperbound: Float = 24.0f / (Math.sqrt(L + D)).toFloat;

    val reps: Int = args(1).toInt

    val dataPoints = sc.textFile(args(0)).map(line => {
      line.split(" ").map(e => e.toFloat)
    }).cache()

    var w0 = Array.tabulate(L * (D+1))(e => (rand.nextFloat - 0.5f) * 2.0f * upperbound)

    val pointNum = dataPoints.count

    var k: Int = 0
    while (k < ITERATION) {
      var start_time = System.nanoTime
      val weight: Array[Float] = w0
      //        val b_w = sc.broadcast(w)

      val gradient = dataPoints.map(in => {
        val _L: Int = 10
        val _D: Int = 784

        val grad = new Array[Float](7840)
        var dot = 0.0f
        //val w = b_w.value

        var i: Int = 0
        while (i < _L) {
          var j: Int = 0
          while (j < _D) {
            dot += weight(i * _D + j) * in(j + _L)
            j += 1
          }

          val c = (1.0f / (1.0f + Math.exp(-in(i) * dot).toFloat) - 1.0f) * in(i)

          j = 0
          while (j < _D) {
            grad(i * _D + j) = 0.0f
            j += 1
          }          
          j = 0
          while (j < _D) {
            grad(i * _D + j) += c * in(j + _L)
            j += 1
          }
          i += 1
        }
        grad
      })

      w0 = weight.zip(gradient.reduce((a, b) => a.zip(b).map({case(x, y) => x + y})))
        .map({case(a, b) => a - 0.13f * b / pointNum })
      var elapsed_time = System.nanoTime - start_time
      System.out.println("On iteration " + k + " Time: "+ elapsed_time/1e6 + "ms")

      // Verification 
      val errNum = dataPoints
        .map(points => predictor(w0, points))
        .reduce((a, b) => (a + b))
        println("Error rate: " + ((errNum.toFloat / pointNum.toFloat) * 100) + "%")

        k += 1
    }
    sc.stop
  }

  def predictor(w: Array[Float], data: Array[Float]): Int = {
    val L = 10
    val D = 784

    val maxPred = new Array[Float](1)
    val maxIdx = new Array[Int](1)
    maxPred(0) = 0.0f

    for (i <- 0 until L) {
      val dot = new Array[Float](1)
      dot(0) = 0.0f
      for(j <- 0 until D)
        dot(0) = dot(0) + w(i * D + j) * data(L + j)
        dot(0) = dot(0) + w(i * D)
        val pred = 1 / (1 + Math.exp(-dot(0)).toFloat)
        if (pred > maxPred(0)) {
          maxPred(0) = pred
          maxIdx(0) = i
        }
    }
    if (data(maxIdx(0)) < 0.5)
      1
    else
      0
  }
}

