import org.apache.spark.SparkContext._
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.blaze._

import scala.math._

object NBody1D {

  def main(args: Array[String]) {
    if (args.length < 1) {
      System.err.println("Usage: NBody1D <#body (K)> <iter>")
      System.exit(1)
    }

    val sparkConf = new SparkConf().setAppName("NBody1D")
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

      var x = (radius * cos(theta) * sin(phi)).toFloat
      if (e % 2 == 0)
        x += maxDist * 1.5f
      else
        x -= maxDist * 1.5f
      (x, 0.0f)
    }).toArray


    for (i <- 1 to iters) {
      val b_bodies = acc.wrap(sc.broadcast(bodies.map(e => e._1)))
      val bodyRDD = acc.wrap(sc.parallelize(bodies, npartition))
      val new_bodies: Array[Array[Float]] = bodyRDD.map_acc(new NBody1D(b_bodies)).collect
      bodies = new_bodies.map(b => (b(0), b(1)))
      println("Iteration " + i + " done, first body: " + bodies(0)._1 + ", " + bodies(0)._2)
    }

    acc.stop()
  }
}

class NBody1D(bodies: BlazeBroadcast[Array[Float]]) 
  extends Accelerator[Tuple2[Float, Float], Array[Float]] {

  val id: String = "NBody1D"

  def getArgNum = 1

  def getArg(idx: Int) = idx match {
    case 0 => Some(bodies)
    case _ => None
  }

  override def call(this_body: Tuple2[Float, Float]): Array[Float] = {
    val all_bodies = bodies.value
    val body_num = (bodies.value).length
    var i: Int = 0

    var this_acc = 0.0f

    while (i < body_num) {
      val cur_body = all_bodies(i)

      val dx = cur_body - this_body._1
      if (dx != 0) {
        val distSqr = dx * dx
        val distSixth = distSqr * distSqr * distSqr
        val dist = 1.0f / sqrt(distSixth).toFloat
        val s = 5f * dist
        this_acc = this_acc + (s * dx)
      }
      i += 1
    }
    this_acc = this_acc * 0.005f

    val new_body = new Array[Float](2)
    new_body(0) = this_body._1 + (this_body._2 * 0.005f) + (this_acc * 0.5f * 0.005f)
    new_body(1) = this_body._2 + this_acc
    new_body
  }
}


