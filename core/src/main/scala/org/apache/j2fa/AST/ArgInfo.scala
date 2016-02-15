package org.apache.j2fa.AST

import scala.reflect.runtime.universe._

class ArgInfo(argName: String) {
  var thisType: TypeInfo = null

  def this() = {
    this(null)
  }

  def setType(t: TypeInfo) = {
    thisType = t
  }

  def getName = argName

  def getTypeName = thisType.getName

  def getShortTypeName = thisType.getShortName

  override def toString() = {
    var str = if (argName == null) "" else argName + ": "

    str += thisType.toString
    str
  }
}

