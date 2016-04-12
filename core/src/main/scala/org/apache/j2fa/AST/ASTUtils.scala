package org.apache.j2fa.AST

import scala.reflect.runtime.universe._
import scala.tools.reflect.ToolBox
import scala.io.Source

object ASTUtils {

  def getSourceTree(filePath: String) = {
    val tb = runtimeMirror(getClass.getClassLoader).mkToolBox()
    var code = Source.fromFile(filePath).getLines.mkString("\n")

    /* Scala 2.11 bug SI-6657:
      package declaration must be along with brackets.
      Origin: package PACKAGE_NAME
                // source code
      Required: package PACKAGE_NAME {
                  // source code
                }
    */
    if (code.contains("package ")) {
      val pos = code.indexOf("\n", code.indexOf("package"))
      code = code.substring(0, pos) + "{" + code.substring(pos, code.length) + "}"
    }
    tb.parse(code)
  }

  def getKernelInfo(tree: Tree) = {
    val extractor = new KernelInfoExt("J2FA_Kernel")
    extractor.traverse(tree)
    extractor
  }
}
