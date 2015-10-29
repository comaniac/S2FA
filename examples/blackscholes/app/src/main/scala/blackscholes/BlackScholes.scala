import org.apache.spark.SparkContext._
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.blaze._

import scala.math._

object BlackScholes {

  def main(args: Array[String]) {
    if (args.length < 1) {
      System.err.println("Usage: BlackScholes <size> <iter>")
      System.exit(1)
    }

    val sparkConf = new SparkConf().setAppName("BlackScholes")
    val iters = if (args.length > 0) args(1).toInt else 1
    val num = args(0).toInt
    val npartition = args(1).toInt
    val sc = new SparkContext(sparkConf)
    val acc = new BlazeRuntime(sc)

    val data = (0 until num).map(e => {
      (e * 1.0f / num).toFloat
    }).toArray

    val rdd = acc.wrap(sc.parallelize(data, npartition))
    val result = rdd.map_acc(new BlackScholes).collect
    println("First result: " + result(0)(0) + ", " + result(0)(1))

    acc.stop()
  }
}

class BlackScholes extends Accelerator[Float, Array[Float]] {

  val id: String = "BlackScholes"

  def getArgNum = 0

  def getArg(idx: Int) = idx match {
    case _ => None
  }

  def phi(x: Float): Float = {
    var absX = 0.0f
    if (x > 0)
      absX = x
    else
      absX = -x

    val t = 1.0f / (1.0f + 0.2316419f * absX)
    val y = 1.0f - 0.398942280f * exp(-x * x / 2.0f).toFloat * t * 
            (0.319381530f + t * (-0.356563782f + t * (1.781477937f + 
            t * (-1.821255978f + t * 1.330274429f))))

    if (x < 0.0f)
      1.0f - y
    else
      y
  }

  override def call(in: Float): Array[Float] = {
    val my_in = in
    val S = 10.0f * my_in + 100.0f * (1.0f - my_in)
    val K = 10.0f * my_in + 100.0f * (1.0f - my_in)
    val T = 1.0f * my_in + 10.0f * (1.0f - my_in)
    val R = 0.01f * my_in + 0.05f * (1.0f - my_in)
    val sigmaVal = 0.01f * my_in + 0.10f * (1.0f - my_in)

    val sigmaSqrtT = sigmaVal * sqrt(T).toFloat;
    val d1 = (math.log(S / K).toFloat + (R + sigmaVal * sigmaVal / 2.0f) * T) / sigmaSqrtT
    val d2 = d1 - sigmaSqrtT

    val KexpMinusRT = K * exp(-R * T).toFloat

    val c = S * phi(d1) - KexpMinusRT * phi(d2)
    val p = KexpMinusRT * phi(-d2) - S * phi(-d1)

    val out = new Array[Float](2)
    out(0) = c
    out(1) = p
    out
  }
}


