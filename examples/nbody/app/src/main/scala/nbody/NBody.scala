import org.apache.spark.SparkContext._
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.blaze._

import scala.math._

object NBody {

  def main(args: Array[String]) {
    if (args.length < 1) {
      System.err.println("Usage: NBody <#body (K)> <iter>")
      System.exit(1)
    }

    val sparkConf = new SparkConf().setAppName("NBody")
    val iters = if (args.length > 0) args(1).toInt else 1
    val npartition = if (args.length > 1) args(2).toInt else 1
    val nbody = args(0).toInt
    val sc = new SparkContext(sparkConf)
    val acc = new BlazeRuntime(sc)

    val maxDist = 20f
    var bodies = (0 until nbody).map(e => {
      val theta = (random * Pi * 2).toFloat
      val phi = (random * Pi * 2).toFloat
      val radius = (random * maxDist).toFloat

      var x = (radius * cos(theta) * sin(phi)).toFloat;
      var y = (radius * sin(theta) * sin(phi)).toFloat;
      var z = (radius * cos(phi)).toFloat;

      if (e % 2 == 0)
        x += maxDist * 1.5f
      else
        x -= maxDist * 1.5f

      Array(x, y, z, 0.0f, 0.0f, 0.0f)
    }).toArray


    for (i <- 1 to iters) {
      val flat_bodies = bodies.flatMap(e => e)
      val b_bodies = acc.wrap(sc.broadcast(flat_bodies))
      val bodyRDD = acc.wrap(sc.parallelize(bodies, npartition))
      bodies = bodyRDD.map_acc(new NBody(b_bodies)).collect
      val first_body = bodies(0)

      println("Iteration " + i + " done, first body: " + 
        first_body(0) + ", " + first_body(1) + ", " + first_body(2) + ", " + 
        first_body(3) + ", " + first_body(4) + ", " + first_body(5))

    }

    acc.stop()
  }
}

class NBody(bodies: BlazeBroadcast[Array[Float]]) 
  extends Accelerator[Array[Float], Array[Float]] {

  val id: String = "NBody"

  def getArgNum = 1

  def getArg(idx: Int) = idx match {
    case 0 => Some(bodies)
    case _ => None
  }

  override def call(in: Array[Float]): Array[Float] = {
    val all_bodies = bodies.value
    val body_num = ((bodies.value).length) / 6
    var i: Int = 0

    val this_body = new Array[Float](6)
    while (i < 6) {
      this_body(i) = in(i)
      i += 1
    }

    val this_acc = new Array[Float](3)
    while (i < 3) {
      this_acc(i) = 0.0f
      i += 1
    }

    while (i < body_num) {
      val cur_body = new Array[Float](3)
      var j: Int = 0
      while (j < 3) {
        cur_body(j) = all_bodies(i * 6 + j)
        j += 1
      }

      val dx = cur_body(0) - this_body(0)
      val dy = cur_body(1) - this_body(1)
      val dz = cur_body(2) - this_body(2)
      val distSqr = dx * dx + dy * dy + dz * dz
      val distSixth = distSqr * distSqr * distSqr
      val dist = 1.0f / sqrt(distSixth).toFloat
      val s = 5f * dist
      this_acc(0) = this_acc(0) + (s * dx)
      this_acc(1) = this_acc(0) + (s * dy)
      this_acc(2) = this_acc(0) + (s * dz)

      i += 1
    }
    this_acc(0) = this_acc(0) * 0.005f
    this_acc(1) = this_acc(1) * 0.005f
    this_acc(2) = this_acc(2) * 0.005f

    val out = new Array[Float](6)
    i = 0
    while (i < 3) {
      out(i) = this_body(i) + (this_body(i + 3) * 0.005f) + (this_acc(i) * 0.5f * 0.005f)
      i += 1
    }
    i = 0
    while (i < 3) {
      out(i + 3) = this_body(i + 3) + this_acc(i)
      i += 1
    }
    out
  }
}


