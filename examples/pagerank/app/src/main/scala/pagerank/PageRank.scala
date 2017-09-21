import java.io.{File, PrintWriter, IOException}
import java.net._

import scala.util.Random
import org.apache.spark.SparkContext._
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.HashPartitioner
import org.apache.spark.Partitioner
import org.apache.spark.rdd._

import scala.math._
import Array._

object PageRank {
  def main(args : Array[String]) {
    if (args.length != 3) {
      println("usage: SparkPageRank iters input-link-path input-docs-path");
      return
    }
    val sparkConf = new SparkConf().setAppName("Page Rank")
    val sc = new SparkContext(sparkConf)

    val iters = args(0).toInt
    val inputLinksPath = args(1)
    val inputDocsPath = args(2)

    /*
     * The convention used here is that link._1 is the destination node of a
     * link, link._2 is the source node of a link
     */
    val raw_links : RDD[Tuple2[Int, Int]] = sc.objectFile[Tuple2[Int, Int]](
      inputLinksPath).cache
    val links = raw_links

    val raw_docs : RDD[Tuple2[Float, Int]] = sc.objectFile(inputDocsPath)
    val collected_docs : Array[Tuple2[Float, Int]] = raw_docs.collect
    System.err.println("Processing " + collected_docs.length + " documents")

    val doc_ranks : Array[Float] = new Array[Float](collected_docs.length)
    val doc_link_counts : Array[Int] = new Array[Int](collected_docs.length)
    for (i <- collected_docs.indices) {
      doc_ranks(i) = collected_docs(i)._1
      doc_link_counts(i) = collected_docs(i)._2
    }

    val bDocLinkCounts = sc.broadcast(doc_link_counts)

    val startTime = System.currentTimeMillis
    var iter = 0
    while (iter < iters) {
      val iterStart = System.currentTimeMillis
      val bRanks = sc.broadcast(doc_ranks)

      val linkWeights : RDD[(Int, Float)] = links.map(in => {
        val destNode = in._1
        val srcNode = in._2
        val rs = doc_ranks
        val cs = doc_link_counts
        val r = rs(srcNode)
        val c = cs(srcNode)
        val w = r / c

        (in._1, w)
      })

      val newRanksRDD : RDD[(Int, Float)] = linkWeights.reduceByKey(
        (weight1, weight2) => { weight1 + weight2 })
      val newRanks : Array[(Int, Float)] = newRanksRDD.collect

      /*
       * newRanks.length may not equal doc_ranks.length if a given
       * document has no documents targeting it. In that case, its rank is
       * static and it will not be included in the updated ranks.
       */
      for (update <- newRanks) {
        doc_ranks(update._1) = update._2
      }

      iter += 1

      val iterEnd = System.currentTimeMillis
      System.err.println("iter " + iter + ", iter time=" +
        (iterEnd - iterStart) + ", program time so far=" +
        (iterEnd - startTime))
    }

    val endTime = System.currentTimeMillis
    System.err.println("Overall time = " + (endTime - startTime))
  }
}
