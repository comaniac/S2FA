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

          var j = 0
          while (j < 6) {
            writer.write("%.4f".format(Random.nextFloat) + " ")
            j += 1
          }
          writer.write("\n")
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

    val rdd = acc.wrap(sc.textFile(filePath).map(line => {
      val sdata = line.split(" ")
      val data = sdata.map(e => e.toFloat)
      new OptionData(data(0), data(1), data(2), data(3), data(4), data(5))
    }).repartition(npar))

    val t0 = System.nanoTime
    val firstResult = rdd.map_acc(new BlackScholes).first
    val t1 = System.nanoTime
    println("Elapsed time: " + ((t1 - t0) / 1e+9) + "s")
    println("First result: " + firstResult)

    acc.stop()
  }
}

class BlackScholes extends Accelerator[OptionData, Float] {

  val id: String = "BlackScholes"

  def getArgNum = 0

  def getArg(idx: Int) = idx match {
    case _ => None
  }

  def CNDF(inputX: Float): Float = {
    var sign: Int = 0

    var outputX: Float = 0
    var xInput: Float = 0
    var xNPrimeofX: Float = 0
    var expValues: Float = 0
    var xK2: Float = 0
    var xK2_2: Float = 0
    var xK2_3: Float = 0
    var xK2_4: Float = 0
    var xK2_5: Float = 0
    var xLocal: Float = 0
    var xLocal_1: Float = 0
    var xLocal_2: Float = 0
    var xLocal_3: Float = 0

    // Check for negative value of inputX
    if (inputX < 0.0f) {
        xInput = -inputX
        sign = 1
    } else {
        xInput = inputX
        sign = 0
    }

    // Compute NPrimeX term common to both four & six decimal accuracy calcs
    expValues = math.exp(-0.5f * inputX * inputX).toFloat
    xNPrimeofX = expValues
    xNPrimeofX = xNPrimeofX * 0.39894228040143270286f

    xK2 = 0.2316419f * xInput
    xK2 = 1.0f + xK2
    xK2 = 1.0f / xK2
    xK2_2 = xK2 * xK2
    xK2_3 = xK2_2 * xK2
    xK2_4 = xK2_3 * xK2
    xK2_5 = xK2_4 * xK2

    xLocal_1 = xK2 * 0.319381530f
    xLocal_2 = xK2_2 * (-0.356563782f)
    xLocal_3 = xK2_3 * 1.781477937f
    xLocal_2 = xLocal_2 + xLocal_3
    xLocal_3 = xK2_4 * (-1.821255978f)
    xLocal_2 = xLocal_2 + xLocal_3
    xLocal_3 = xK2_5 * 1.330274429f
    xLocal_2 = xLocal_2 + xLocal_3

    xLocal_1 = xLocal_2 + xLocal_1
    xLocal   = xLocal_1 * xNPrimeofX
    xLocal   = 1.0f - xLocal

    outputX = if (sign == 1) 1.0f - xLocal else xLocal
    outputX
  } 

  @J2FA_Kernel
  override def call(in: OptionData): Float = {
    var optionPrice: Float = 0

    // local private working variables for the calculation
    var xStockPrice: Float = 0
    var xStrikePrice: Float = 0
    var xRiskFreeRate: Float = 0
    var xVolatility: Float = 0
    var xTime: Float = 0
    var xSqrtTime: Float = 0

    var logValues: Float = 0
    var xLogTerm: Float = 0
    var xPowerTerm: Float = 0
    var xD1: Float = 0
    var xD2: Float = 0
    var xPowerTer: Float = 0
    var xDen: Float = 0
    var d1: Float = 0
    var d2: Float = 0
    var futureValueX: Float = 0
    var nofXd1: Float = 0
    var nofXd2: Float = 0
    var negnofXd1: Float = 0
    var negnofXd2: Float = 0

    xStockPrice = in.sptprice
    xStrikePrice = in.strike
    xRiskFreeRate = in.rate
    xVolatility = in.volatility

    xTime = in.otime;
    xSqrtTime = math.sqrt(xTime).toFloat
    logValues = in.sptprice / in.strike
    xLogTerm = logValues

    xPowerTerm = xVolatility * xVolatility
    xPowerTerm = xPowerTerm * 0.5f

    xD1 = xRiskFreeRate + xPowerTerm
    xD1 = xD1 * xTime
    xD1 = xD1 + xLogTerm

    xDen = xVolatility * xSqrtTime
    xD1 = xD1 / xDen
    xD2 = xD1 - xDen

    d1 = xD1
    d2 = xD2

    nofXd1 = CNDF(d1)
    nofXd2 = CNDF(d2)

    futureValueX = xStrikePrice * math.exp(-(xRiskFreeRate)*(xTime)).toFloat
    if (in.otype == 0) {
        optionPrice = (xStockPrice * nofXd1) - (futureValueX * nofXd2)
    } else { 
        negnofXd1 = (1.0f - nofXd1)
        negnofXd2 = (1.0f - nofXd2)
        optionPrice = (futureValueX * negnofXd2) - (xStockPrice * negnofXd1)
    }
    optionPrice
  }
}

class OptionData(
  var sptprice: Float, 
  var strike: Float, 
  var rate: Float, 
  var volatility: Float, 
  var otime: Float, 
  var otype: Float) extends java.io.Serializable {
}
