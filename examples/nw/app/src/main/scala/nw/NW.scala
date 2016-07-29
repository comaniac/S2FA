import java.io.{File, PrintWriter, IOException}
import scala.util.Random
import org.apache.spark.SparkContext._
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.blaze._
import org.apache.j2fa.Annotation._

import scala.math._

object NW {

  def main(args: Array[String]) {
    if (args.length < 1) {
      System.err.println("Usage: NW <input-file>")
      System.exit(1)
    }

    val sparkConf = new SparkConf().setAppName("NW")
    val filePath = args(0)
    val sc = new SparkContext(sparkConf)
    val acc = new BlazeRuntime(sc)

    val rdd = acc.wrap(sc.textFile(filePath).map(line => {
      val sdata = line.split(" ")
      (sdata(0).toCharArray.map(e => e.toInt), 
      sdata(1).toCharArray.map(e => e.toInt))
    }))

    val t0 = System.nanoTime
    val firstResult = rdd.map_acc(new NW).first
    val t1 = System.nanoTime
    println("Elapsed time: " + ((t1 - t0) / 1e+9) + "s")
    println("First result: " + firstResult._1.map(e => e.toChar).mkString)

    acc.stop
  }
}

class NW extends Accelerator[Tuple2[Array[Int], Array[Int]], 
  Tuple2[Array[Int], Array[Int]]] {

  val id: String = "NW"

  def getArgNum = 0

  def getArg(idx: Int) = idx match {
    case _ => None
  }

  @J2FA_Kernel
  override def call(in: Tuple2[Array[Int], Array[Int]]): 
    Tuple2[Array[Int], Array[Int]] = {

    val MATCH_SCORE = 1
    val MISMATCH_SCORE = -1
    val GAP_SCORE = -1
    val ALIGN = 92 // \
    val SKIPA = 94 // ^
    val SKIPB = 60 // <

    var i = 0
    var j = 0

    val seqA = in._1
    val seqB = in._2
    val alignA = new Array[Int](256)
    val alignB = new Array[Int](256)
    val mFormer = new Array[Int](129)
    val mLatter = new Array[Int](129)
    val ptr = new Array[Int](129 * 129)

    while (i < 129) {
      mFormer(i) = i * GAP_SCORE
      i += 1
    }

    // Matric filling loop
    var b_idx = 1
    var a_idx = 1
    while (b_idx < 129) {
      mLatter(0) = mFormer(0) + GAP_SCORE
      a_idx = 1
      while (a_idx < 129) {
        val score = if (seqA(a_idx - 1) == seqB(b_idx - 1)) {
          MATCH_SCORE
        }
        else {
          MISMATCH_SCORE
        }

        val row = b_idx * 129
        val up_left = mFormer(a_idx - 1) + score
        val up      = mFormer(a_idx) + GAP_SCORE
        val left    = mLatter(a_idx - 1) + GAP_SCORE
        var maxScore = if (up > left) {
          up
        }
        else {
          left
        }
        maxScore = if (up_left > maxScore) {
          up_left
        }
        else {
          maxScore
        }

        mLatter(a_idx) = maxScore
        if (maxScore == left) {
          ptr(row + a_idx) = SKIPB
        }
        else if (maxScore == up) {
          ptr(row + a_idx) = SKIPA
        }
        else {
          ptr(row + a_idx) = ALIGN
        }
        a_idx += 1
      }
      var k = 0
      while (k < 129) {
        mFormer(k) = mLatter(k)
        k += 1
      }
      b_idx += 1
    }

    // Traceback
    a_idx = 128
    b_idx = 128
    var a_str_idx = 0
    var b_str_idx = 0
    while (a_idx > 0 || b_idx > 0) {
      val r = b_idx * 129
      if (ptr(r + a_idx) == ALIGN) {
        alignA(a_str_idx) = seqA(a_idx - 1)
        alignB(b_str_idx) = seqB(b_idx - 1)
        a_idx -= 1
        b_idx -= 1
      }
      else if (ptr(r + a_idx) == SKIPB) {
        alignA(a_str_idx) = seqA(a_idx - 1)
        alignB(b_str_idx) = '-'
        a_idx -= 1
      }
      else {
        alignA(a_str_idx) = '-'
        alignB(b_str_idx) = seqB(b_idx - 1)
        b_idx -= 1
      }
      a_str_idx += 1
      b_str_idx += 1
    }

    // Pad the result
    var a_pad = a_str_idx
    while (a_pad < 256) {
      alignA(a_pad) = '_'
      a_pad += 1
    }

    var b_pad = b_str_idx
    while (b_pad < 256) {
      alignB(b_pad) = '_'
      b_pad += 1
    }

    (alignA, alignB)
  }
}

