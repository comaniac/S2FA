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

// scalastyle:off println
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext._
import org.apache.spark.SparkConf
import Array._
import scala.math._
import org.apache.spark.rdd._
import java.net._

/**
 * K-means clustering.
 */
object SparkKMeans {
  def get_spark_context(appName : String) : SparkContext = {
    val conf = new SparkConf()
    conf.setAppName(appName)

    return new SparkContext(conf)
  }
 
  def squaredDistance(p: Array[Double], center: Array[Double]): Double = {
    var i: Int = 0
    var dis: Double = 0
    while (i < p.length) {
      dis += (p(i) - center(i)) * (p(i) - center(i))
      i += 1
    }
    dis
  }

  def parseVector(line: String): Array[Double] = {
    line.split(' ').map(e => e.toDouble)
  }

  def closestPoint(p: Array[Double], centers: Array[Array[Double]]): Int = {
    var bestIndex = 0
    var closest = Double.PositiveInfinity

    for (i <- 0 until centers.length) {
      val tempDist = squaredDistance(p, centers(i))
      if (tempDist < closest) {
        closest = tempDist
        bestIndex = i
      }
    }

    bestIndex
  }

  def main(args: Array[String]) {

    if (args.length < 3) {
      System.err.println("Usage: SparkKMeans <file> <k> <convergeDist>")
      System.exit(1)
    }

    val spark = get_spark_context("Spark KMeans")

    val lines = spark.textFile(args(0))
    val data = lines.map(parseVector _).cache()
    val K = args(1).toInt
    val convergeDist = args(2).toDouble

    val kPoints = data.takeSample(withReplacement = false, K, 42)
    var tempDist = 1.0

    while(tempDist > convergeDist) {
      val closestIdx = data.map(p => closestPoint(p, kPoints))
      val closest = data.zip(closestIdx).map{case (p, i) => (i, (p, 1))}

      val pointStats = closest.reduceByKey{case ((p1, c1), (p2, c2)) =>
        (p1.zip(p2).map{case (x, y) => x + y}, c1 + c2)
      }

      val newPoints = pointStats.map {pair =>
        (pair._1, pair._2._1.map(_ * (1.0 / pair._2._2)))}.collectAsMap()

      tempDist = 0.0
      for (i <- 0 until K) {
        tempDist += squaredDistance(kPoints(i), newPoints(i))
      }

      for (newP <- newPoints) {
        kPoints(newP._1) = newP._2
      }
      println("Finished iteration (delta = " + tempDist + ")")
    }

    spark.stop()
  }
}
// scalastyle:on println
