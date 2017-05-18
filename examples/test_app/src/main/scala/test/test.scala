import java.io.{File, PrintWriter, IOException}
import scala.io.Source
import scala.util.Random
import org.apache.spark.SparkContext._
import org.apache.spark.{SparkConf, SparkContext}

object test {

  def main(args: Array[String]) {

    val sparkConf = new SparkConf().setAppName("AES")
    val sc = new SparkContext(sparkConf)
    val ref_point = 3
    val ref_ary = Array(1, 3, 5)

    val rdd = sc.parallelize(Array(Array(1, 2, 3, 4, 5), Array(1, 2, 3, 4, 5), Array(1, 2, 3, 4, 5)))
    val trans = rdd.map(e => {
        var i = 0
        val ret = new Array[Int](5)
        while (i < 5) {
          ret(i) = e(i) + 1 + ref_point
          i += 1
        }
        ret
      }).map(e => {
        var i = 0
        val ret = new Array[Int](5)
        while (i < 5) {
          ret(i) = e(i) * 2 + ref_ary(0) + ref_ary(1)
          i += 1
        }        
        ret
      })
      //.zipWithIndex
    val res = trans.collect
    res.foreach(e => println(e))
  }
}

