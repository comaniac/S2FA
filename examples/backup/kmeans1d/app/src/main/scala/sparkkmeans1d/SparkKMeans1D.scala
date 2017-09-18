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
import Array._
import scala.math._
import org.apache.spark.rdd._
import java.net._

import org.apache.spark.blaze._

object SparkKmeans1D {
    def main(args : Array[String]) {
      if (args.length != 4) {
        println("usage: SparkKmeans1D run K iters npartitions input-path");
        return;
      }
      val sc = get_spark_context("Spark Kmeans1D");
      val acc = new BlazeRuntime(sc)

      val K = args(0).toInt
      val iters = args(1).toInt
      val npartitions = args(2).toInt
      val num_data = args(3).toInt

      val points = sc.parallelize(0 until num_data)
        .map(e => {
          (random * 10e3).toInt
        })
        .repartition(npartitions)
        .cache()

      var centers: Array[Int] = points.takeSample(true, K, 99)
      println("Data count = " + points.count)

      val b_centers = acc.wrap(sc.broadcast(centers))
      val classifiedCenters = acc.wrap(points).map_acc(new KMeansClassified1D(b_centers))
      val classified = classifiedCenters.zip(points)
      val counts = classified.countByKey()
      val sums = classified.reduceByKey(_ + _)
      val avg = sums.map(kv => {
        val idx = kv._1
        val p = kv._2
        (p / counts(idx)).toInt
      }).collect
      centers = avg

      println("New centers: ")
      centers.foreach( c => println(c))
    }


    def get_spark_context(appName : String) : SparkContext = {
        val conf = new SparkConf()
        conf.setAppName(appName)
        
        return new SparkContext(conf)
    }
}

class KMeansClassified1D (b_centers: BlazeBroadcast[Array[Int]]) 
  extends Accelerator[Int, Int] {
  
  val id: String = "KMeans1D"

  def getArgNum = 1

  def getArg(idx: Int): Option[_] = {
    if (idx == 0)
      Some(b_centers)
    else
      None
  }

  override def call(in: Int): Int = { 
    val centers = b_centers.value

    val K: Int = 3

    var closest_center = -1
    var closest_center_dist = -1
    var dist = 0

    // Blaze CodeGen: foreach and for loop are forbidden.
    var i: Int = 0
    while (i < K) {
      dist = math.abs(centers(i) - in)
      if (closest_center == -1 || dist < closest_center_dist) {
        closest_center = i
        closest_center_dist = dist
      }
      i += 1
    }
    closest_center
  }
}

