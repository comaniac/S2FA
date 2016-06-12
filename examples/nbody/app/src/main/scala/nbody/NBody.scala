import org.apache.spark.SparkContext._
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.blaze._
import org.apache.j2fa.Annotation._

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

      new Partical(x, y, z, 0.0f, 0.0f, 0.0f)
    }).toArray
    val first_body = bodies(0)
    println("Original first body: " + 
      first_body.x + ", " + first_body.y + ", " + first_body.z + ", " + 
      first_body.ax + ", " + first_body.ay + ", " + first_body.az)


    for (i <- 1 to iters) {
      val b_bodies = acc.wrap(sc.broadcast(bodies))
      val bodyRDD = acc.wrap(sc.parallelize(bodies, npartition))
      bodies = bodyRDD.map_acc(new NBody(b_bodies)).collect
      val first_body = bodies(0)

      println("Iteration " + i + " done, first body: " + 
        first_body.x + ", " + first_body.y + ", " + first_body.z + ", " + 
        first_body.ax + ", " + first_body.ay + ", " + first_body.az)

    }
    acc.stop()
  }
}

class Partical(
  var x: Float, 
  var y: Float, 
  var z: Float, 
  var ax: Float, 
  var ay: Float, 
  var az: Float) extends java.io.Serializable {
}

class NBody(bodies: BlazeBroadcast[Array[Partical]]) 
  extends Accelerator[Partical, Partical] {

  val id: String = "NBody"

  def getArgNum = 1

  def getArg(idx: Int) = idx match {
    case 0 => Some(bodies)
    case _ => None
  }

  @J2FA_Kernel
  override def call(in: Partical): Partical = {
    val all_bodies = bodies.value
    val body_num = (bodies.value).length
    var i: Int = 0

    val out = new Partical(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)

    i = 0
    while (i < body_num) {
      val dx = all_bodies(i).x - in.x
      val dy = all_bodies(i).y - in.y
      val dz = all_bodies(i).z - in.z
      val distSqr = dx * dx + dy * dy + dz * dz
      if (distSqr != 0) {
        val distSixth = distSqr * distSqr * distSqr
        val dist = 1.0f / sqrt(distSixth).toFloat
        val s = 5f * dist
        out.ax += s * dx
        out.ay += s * dy
        out.az += s * dz
      }

      i += 1
    }
    out.ax *= 0.005f
    out.ay *= 0.005f
    out.az *= 0.005f

    out.x = in.x + (in.ax * 0.005f + out.ax * 0.5f * 0.005f)
    out.y = in.y + (in.ay * 0.005f + out.ay * 0.5f * 0.005f)
    out.z = in.z + (in.az * 0.005f + out.az * 0.5f * 0.005f)

    out.ax += in.ax
    out.ay += in.ay
    out.az += in.az

    out
  }
}


