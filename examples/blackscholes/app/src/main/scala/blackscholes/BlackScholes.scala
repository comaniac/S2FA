import java.io.{File, PrintWriter, IOException}
import scala.util.Random
import org.apache.spark.SparkContext._
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.blaze._
import org.apache.j2fa.Annotation._

import scala.math._

object BlackScholes {

  def main(args: Array[String]) {
    if (args.length < 3) {
      System.err.println("Usage: BlackScholes <mode:g|c> <size|filename> <npar>")
      System.exit(1)
    }

    // Generate an input data set
    if (args(0).equals("g")) {
      val size = args(1).toLong
      try {
        val writer = new PrintWriter(new File("set0000.dat"))
        var i = 0L
        while (i < size) {
          if (i % 1000000 == 0)
            print("%.2f".format(i / size.toDouble * 100.0) + "%...")
          writer.write("%.4f".format(Random.nextFloat) + "\n")
          i += 1
        }
        println
        writer.close
      } catch {
        case e: IOException => println("Fail to generate a data set")
      }
      System.exit(1)
    }

    val sparkConf = new SparkConf().setAppName("BlackScholes")
    val filePath = args(1)
    val npar = args(2).toInt
    val sc = new SparkContext(sparkConf)
    val acc = new BlazeRuntime(sc)

    val rdd = acc.wrap(sc.textFile(filePath).map(line => line.toFloat).repartition(npar))

    val t0 = System.nanoTime
    val firstResult = rdd.map_acc(new BlackScholes).first
    val t1 = System.nanoTime
    println("Elapsed time: " + ((t1 - t0) / 1e+9) + "s")
    println("First result: " + firstResult._1 + ", " + firstResult._2)

    acc.stop()
  }
}

class BlackScholes extends Accelerator[Float, Tuple2[Float, Float]] {

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

  @J2FA_Kernel
  override def call(in: Float): Tuple2[Float, Float] = {
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

    (c, p)
  }
}


