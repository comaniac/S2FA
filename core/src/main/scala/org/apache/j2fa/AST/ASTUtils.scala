package org.apache.j2fa.AST

import scala.reflect.runtime.universe._
import scala.tools.reflect.ToolBox
import scala.io.Source

object ASTUtils {

  def getSourceTree(filePath: String) = {
    val tb = runtimeMirror(getClass.getClassLoader).mkToolBox()
    val code = Source.fromFile(filePath).getLines.mkString("\n")
    tb.parse(code)
  }

  def getKernelMethods(tree: Tree) = {
    val extractor = new KernelMethodExt("J2FA_Kernel")
    extractor.traverse(tree)
    extractor.getMethods
  }
}
