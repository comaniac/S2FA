import java.io.{File, PrintWriter, IOException}
import java.net._

import scala.util.Random
import org.apache.spark.SparkContext._
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd._
import org.apache.spark.blaze._
import org.apache.j2fa.Annotation._

import scala.math._

object ValueOfRisk {
  def main(args : Array[String]) {
    if (args.length != 3) {
      println("usage: ValueOfRisk instrument-path numTrials numRuns")
      return
    }
    val sparkConf = new SparkConf().setAppName("Value of Risk")
    val sc = new SparkContext(sparkConf)
    val acc = new BlazeRuntime(sc)

    val instruments = readInstruments(sc, args(0))
    val numTrials = args(1).toInt
    val parallelism = args(2).toInt
    val seed = 99

    // Send all instruments to every node
    val broadcastInstruments = acc.wrap(sc.broadcast(instruments))

    // Generate different seeds so that our simulations don't all end up with the same results
    val seeds = (seed until seed + parallelism)
    val seedRdd = sc.parallelize(seeds, parallelism)
    val blazeRdd = acc.wrap(seedRdd)

    val startTime = System.currentTimeMillis

    // Run simulation
    val tRdd = blazeRdd
      .map_acc(new MonteCarlo(numTrials / parallelism, broadcastInstruments))
      .flatMap(e => e)

    val endTime = System.currentTimeMillis
    println("Sample: " + tRdd.first)
    System.err.println("Overall time = " + (endTime - startTime))
  }

  def readInstruments(sc: SparkContext, inputDir: String): Array[Float] = {
    val input = sc.textFile(inputDir)
    val converted = input.flatMap(line => {
      val tokens = line.split(" ")
      tokens.map(e => e.toFloat)
    })
    converted.toArray
  }
}

class MonteCarlo(
  numTrails: Int,
  instruments: org.apache.spark.blaze.BlazeBroadcast[Array[Float]])
  extends Accelerator[Int, Array[Float]] {

  val id: String = "MonteCarlo"

  def getArgNum = 2

  def getArg(idx: Int) = idx match {
    case 0 => Some(numTrails)
    case 1 => Some(instruments)
    case _ => None
  }

  @J2FA_Kernel
  override def call (in: Int): Array[Float] = {
    val insts = instruments.value
    val trialValues = new Array[Float](1024)
    var i = 0
    while (i < numTrails) {
      val trial = 0.5f + in // FIXME
      var totalValue = 0.0f
      var j = 0
      while (j < 512) { // Instrument #
        var k = 0
        while (k < 1024) { // Instrument value #
          var instTrailValue = trial * insts(j * 1026 + 2 + k)
          var v = if (instTrailValue > insts(j * 1026)) {
            instTrailValue
          }
          else {
            insts(j * 1026)
          }

          if (v < insts(j * 1026 + 1))
            totalValue += v
          else
            totalValue += insts(j * 1026 + 1)
          k += 1
        }
        j += 1
      }
      trialValues(i) = totalValue
      i += 1
    }
    trialValues
  }
}
