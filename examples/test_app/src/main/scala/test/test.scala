import java.io.{File, PrintWriter, IOException}
import scala.io.Source
import scala.util.Random
import org.apache.spark.SparkContext._
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.j2fa.Annotation._

object test {

  @J2FA_Kernel(kernel="rdd")
  def main(args: Array[String]) {

    val sparkConf = new SparkConf().setAppName("AES")
    val sc = new SparkContext(sparkConf)
    val ref_point = 3
    val ref_ary = Array(1, 3, 5)

    val rdd = sc.parallelize(Array(1, 2, 3, 4, 5))
    val trans = rdd.map(e => e + 1 + ref_point).map(e => e * 2 + ref_ary(0) + ref_ary(1))
      //.zipWithIndex
    val res = trans.collect
    res.foreach(e => println(e))
  }
}

