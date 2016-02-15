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

  def hasOutput = if (out == null) false else true

  def setOutput(arg: ArgInfo) = {
    out = arg
  }

  def getOutput = out

  def getName = methodName

  def getConfig = config

  def getArgs = args

  def getSig : String = {
    var str = "("
    args.foreach(e => str += e.getShortTypeName)
    str += ")"
    if (out == null)
      str += "V"
    else
      str += out.getShortTypeName
    str
  }

  def getFullSig = {
    var str = "("
    args.foreach(e => {
      str += e.getFullType
    })
    str += ")"
    if (out == null)
      str += "V"
    else
      str += out.getFullType
    str
  }

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

