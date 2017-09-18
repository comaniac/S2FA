import java.io.{File, PrintWriter, IOException}
import scala.util.Random
import org.apache.spark.SparkContext._
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.mllib.util.MLUtils
import org.apache.spark.mllib.linalg.Vectors
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.blaze._
import org.apache.j2fa.Annotation._

import scala.math._

object SVM {

  def main(args: Array[String]) {
    if (args.length < 1) {
      System.err.println("Usage: SVM <filename>")
      System.exit(1)
    }

    val sparkConf = new SparkConf().setAppName("SVM")
    val filePath = args(0)
    val sc = new SparkContext(sparkConf)
    val acc = new BlazeRuntime(sc)

//    val rdd = MLUtils.loadLibSVMFile(sc, filePath)
    val rdd = sc.textFile(filePath).map(line => {
      val point = line.split(" ").map(e => e.toDouble)
      val features = Vectors.dense(point.slice(10, 794))
      new LabeledPoint(point(0), features)
    })
    val accRdd = acc.wrap(rdd)

    println("Data count # = " + rdd.count + " with features " + rdd.first.features.size)

    val weights = (0 until rdd.first.features.size).map(e => Random.nextDouble).toArray
    val accWeights = acc.wrap(sc.broadcast(weights))

    val t0 = System.nanoTime
    val firstResult = accRdd.map_acc(new SVM(accWeights)).first
    val t1 = System.nanoTime
    println("Elapsed time: " + ((t1 - t0) / 1e+9) + "s")

    acc.stop()
  }
}

class SVM(weights: org.apache.spark.blaze.BlazeBroadcast[Array[Double]]) 
  extends Accelerator[org.apache.spark.mllib.regression.LabeledPoint, Array[Double]] {

  val id: String = "SVM"

  def getArgNum = 1

  def getArg(idx: Int) = idx match {
    case 0 => Some(weights)
    case _ => None
  }

  @J2FA_Kernel
  override def call(in: org.apache.spark.mllib.regression.LabeledPoint): Array[Double] = {
    val feature_length = 784
    val weight_length = 784
    val ts = 16
    val w = weights.value
    val f: org.apache.spark.mllib.linalg.Vector = in.features
    val label = in.label
    val labelScaled = 2 * label - 1.0
    val output = new Array[Double](785)

    var i = 0
    while (i < 29) {
      output(i) = 0.0
      i += 1
    }

    var dotProduct = 0.0
    var j0 = 0
    while (j0 < feature_length / ts) {
      var jj = 0
      while (jj < ts) {
        val j = j0 * ts + jj
        val mult = w(j) * f(j)
        dotProduct += mult
        jj += 1
      }
      j0 += 1
    }

    if (1.0 > labelScaled * dotProduct) {
      j0 = 0
      while (j0 < feature_length / ts) {
        var jj = 0
        while (jj < ts) {
          val j = j0 * ts + jj
          val mult = -labelScaled * f(j)
          output(j) += mult
          jj += 1
        }
        j0 += 1
      }
      output(weight_length) += 1.0 - labelScaled * dotProduct
    }
    output
  }
}


