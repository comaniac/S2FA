import java.io._

object GenerateInput {
    def main(args : Array[String]) {
        if (args.length != 1) {
            println("usage: GenerateInput output-file")
            return
        }

        val outputFile = args(0)
        val nInst = 512
        val nValue = 1024

        val r = new scala.util.Random(1)

        val writer = new PrintWriter(outputFile)
        for (i <- 0 until nInst) {
            writer.write("0.0 100.0")
            for (j <- 0 until nValue) {
              writer.write(" " + r.nextFloat * 100.0f)
            }
            writer.write("\n")
        }
        writer.close()
    }
}
