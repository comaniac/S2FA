package org.apache.j2fa.AST

import scala.reflect.runtime.universe._
import scala.collection.mutable.Map

class MethodInfo(methodName: String, _config: Map[String, String]) {

  val config = _config
  var args: List[ArgInfo] = List[ArgInfo]()
  var out: ArgInfo = null

  // Config default values
  if (config.contains("kernel_type") == false)
    config("kernel_type") = "map"
  if (config.contains("output_format") == false)
    config("output_format") = "Merlin"

  def addArg(arg: ArgInfo) = {
    args = args :+ arg
  }

  def setOutput(arg: ArgInfo) = {
    out = arg
  }

  def getName = methodName

  def getConfig = config

  override def toString = {
    var first = true
    var str = "@J2FA_Kernel("
    for ((k, v) <- config)
      str += k + "=" + v + " "
    str += ") "
    str += methodName + "("
    args.foreach(e => {
      if (first == false)
        str += ", "
      str += e.toString
      first = false
    })
    str += "): "
    if (out == null)
      str += "void"
    else
      str += out.toString
    str
  }
}

