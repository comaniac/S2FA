import java.io.{File, PrintWriter, IOException}

import scala.math._

object Generator {

  def main(args: Array[String]) {
    if (args.length < 1) {
      System.err.println("Usage: Generator <task num>")
      System.exit(1)
    }

    val key = new PrintWriter(new File("./key.data"))
    (0 to 31).foreach(i => key.write(i.toString + " "))
    key.close

    val data = new PrintWriter(new File("./input.data"))    
    (0 to args(0).toInt - 1).foreach(e => data.write("0 17 34 51 68 85 102 119 136 153 170 187 204 221 238 255\n"))
    data.close
  }
}