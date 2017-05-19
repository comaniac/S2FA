import java.io.{File, PrintWriter, IOException}

import scala.math._

object Generator {

  def main(args: Array[String]) {
    if (args.length < 1) {
      System.err.println("Usage: Generator <task num>")
      System.exit(1)
    }

    val data = new PrintWriter(new File("./input.data"))    
    (0 to args(0).toInt - 1).foreach(e => data.write("tcgacgaaataggatgacagc" + 
      "acgttctcgtattagagggccgcggtacaaaccaaatgctgcggcgtacagggcacggggcgctgttc" + 
      "gggagatcgggggaatcgtggcgtgggtgattcgccggc " + 
      "ttcgagggcgcgtgtcgcggtccatcgacatgcccggtcggtgggacgtgggcgcctgatatagagga" + 
      "atgcgattggaaggtcggacgggtcggcgagttgggcccggtgaatctgccatggtcgat\n"))
    data.close
  }
}
