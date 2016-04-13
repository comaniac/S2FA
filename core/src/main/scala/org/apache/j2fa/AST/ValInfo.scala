package org.apache.j2fa.AST

import scala.reflect.runtime.universe._

class ValInfo(valName: String) {
  var thisType: TypeInfo = null

  def this() = {
    this(null)
  }

  def setType(t: TypeInfo) = {
    thisType = t
  }

  def getName = valName

  def getTypeName = thisType.getName

  def getShortTypeName = thisType.getShortName

  def getFullType = { 
    if (thisType != null) 
      thisType.getFullType
    else
      null
  }

  override def toString() = {
    var str = if (valName == null) "" else valName + ": "
    str + getFullType
  }
}

