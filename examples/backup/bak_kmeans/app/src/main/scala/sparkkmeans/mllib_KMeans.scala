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

import scala.math._
import Array._

import org.apache.spark.Logging
import org.apache.spark.mllib.linalg.{Vector, Vectors}
import org.apache.spark.rdd._
import org.apache.spark.storage.StorageLevel

import org.apache.j2fa.Annotation._
import org.apache.spark.blaze._

class KMeans private (
    private var k: Int,
    private var maxIterations: Int,
    private var runs: Int,
    private var initializationMode: String,
    private var initializationSteps: Int,
    private var epsilon: Double,
    private var seed: Long) extends Logging {

    def this() = this(2, 20, 1, KMeans.K_MEANS_PARALLEL, 5, 1e-4, 99)
    def this(
      _k: Int, 
      _maxIter: Int, 
      _runs: Int, 
      _initMode: String) = 
      this(_k, _maxIter, _runs, _initMode, 5, 1e-4, 99)

    def run(data: RDD[Vector]): Double = {
      require(runs == 1) // Only support 1 run currently

      val arrayData: RDD[Array[Double]] = data.map(point => point.toArray)
      runAlgorithm(arrayData)
    }

    def runAlgorithm(data: RDD[Array[Double]]): Double = {
      val sc = data.sparkContext

      val initStartTime = System.nanoTime()
      val acc = new BlazeRuntime(sc)

      val points: ShellRDD[Array[Int]] = acc.wrap(data.map(p => {
        p.map(e => (e * 10e3).toInt)
      }))
      points.cache
      logInfo(s"Total " + (points.collect).length + " points")

      // Random initialize centers
      val samples = data.map(p => {p.map(e => (e * 10e3).toInt)}).takeSample(true, k, 99)
      val dims = samples(0).length
      var centers = new Array[Int](k * dims)
      (0 until k).foreach { i =>
        (0 until dims).foreach { j => 
          centers(i * dims + j) = samples(i)(j)
        }
      }

      val initTimeInSeconds = (System.nanoTime() - initStartTime) / 1e9
      logInfo(s"Initialization with $initializationMode took " + "%.3f".format(initTimeInSeconds) +
        " seconds.")

      var iteration = 0
      val iterationStartTime = System.nanoTime()

      while (iteration < maxIterations) {
        val bcCenters = acc.wrap(sc.broadcast(centers))
        val startTime = System.nanoTime()
        val classifiedCenters = points.map_acc(new KMeansClassified(bcCenters, dims))
        val elapsedTime = (System.nanoTime() - initStartTime) / 1e9
        println(classifiedCenters.count + " points, execution time " + "%.3f".format(initTimeInSeconds) + " s.")
        val classified = classifiedCenters.zip(points)

        val counts = classified.countByKey()
        val sums = classified.reduceByKey((a, b) => {
          val ary = new Array[Int](dims)
          (0 until dims).foreach ( ii => {
            ary(ii) = a(ii) + b(ii)
          })
          ary
        })

        val averages = sums.map(kv => {
          val cluster_index: Int = kv._1;
          val p: Array[Int] = kv._2;
          val ary = new Array[Int](dims)
          (0 until dims).foreach( ii => {
            ary(ii) = (p(ii) / counts(cluster_index)).toInt
          })
          ary
        }).collect

        (0 until k).foreach { i =>
          (0 until dims).foreach { j => 
            centers(i * dims + j) = averages(i)(j)
          }
        }
        iteration += 1
      }

      val iterationTimeInSeconds = (System.nanoTime() - iterationStartTime) / 1e9
      logInfo(s"Iterations took " + "%.3f".format(iterationTimeInSeconds) + " seconds.")
      val cost = computeCost(centers.map(e => e.toDouble), points.map(p => (p.map(e => e.toDouble))), dims)
      acc.stop

      cost
    }

    def computeCost(centers: Array[Double], data: RDD[Array[Double]], dims: Int): Double = {
      val k = centers.length / dims

      val cost = data.map(point => {
        var minDis: Double = 1e10
        (0 until k).foreach (c => {
          var dis = 0.0
          (0 until dims).foreach (ii => {
            dis += (centers(c * dims + ii) - point(ii)) * (centers(c * dims + ii) - point(ii))
          })
          if (dis < minDis)
            minDis = dis
        })
        minDis
      }).reduce((a, b) => (a + b))

      cost
    }   
}

/**
 * Top-level methods for calling K-means clustering.
 */
object KMeans {

  // Initialization mode names
  val RANDOM = "random"
  val K_MEANS_PARALLEL = "k-means||"

  /**
   * Trains a k-means model using the given set of parameters.
   *
   * @param data training points stored as `RDD[Vector]`
   * @param k number of clusters
   * @param maxIterations max number of iterations
   * @param runs number of parallel runs, defaults to 1. The best model is returned.
   * @param initializationMode initialization model, either "random" or "k-means||" (default).
   */
  def train(
      data: RDD[Vector],
      k: Int,
      maxIterations: Int,
      runs: Int,
      initializationMode: String): Double = {
    new KMeans(k, maxIterations, runs, initializationMode)
      .run(data)
  }

  /**
   * Trains a k-means model using specified parameters and the default values for unspecified.
   */
  def train(
      data: RDD[Vector],
      k: Int,
      maxIterations: Int): Double = {
    train(data, k, maxIterations, 1, K_MEANS_PARALLEL)
  }
}

class KMeansClassified(
  b_centers: BlazeBroadcast[Array[Int]], 
  b_D: Int
  ) extends Accelerator[Array[Int], Int] {
  
  val id: String = "KMeans"

  def getArgNum = 2

  def getArg(idx: Int): Option[_] = {
    if (idx == 0)
      Some(b_centers)
    else if (idx == 1)
      Some(b_D)
    else
      None
  }

  @J2FA_Kernel
  override def call(in: Array[Int]): Int = { 
    val centers = b_centers.value
    val D: Int = b_D

    // Blaze CodeGen: Cannot access array length of local array.
    val K: Int = (b_centers.value).length / D

    var closest_center = -1
    var closest_center_dist = -1

    // Blaze CodeGen: foreach and for loop are forbidden.
    var i: Int = 0
    var dist = 0
    while (i < K) {
      dist = 0

      var j: Int = i
      while (j < D) {
        dist = dist + math.abs(centers(i * D + j) - in(j))
        j += 1
      }
      if (closest_center == -1 || dist < closest_center_dist) {
        closest_center = i
        closest_center_dist = dist
      }
      i += 1
    }
    closest_center
  }
}

