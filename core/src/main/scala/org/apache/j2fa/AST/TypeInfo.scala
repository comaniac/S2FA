package org.apache.j2fa.AST

import scala.reflect.runtime.universe._

class TypeInfo(var typeName: String) {
  var gTypes: List[TypeInfo] = List[TypeInfo]()

  def this() = {
    this(null)
  }

  def setName(name: String) = {
    typeName = name
  }

  def getName = typeName

  def getShortName : String = typeName.replace("scala.", "") match {
    case "Int" => "I"
    case "Float" => "F"
    case "Double" => "D"
    case "Long" => "L"
    case "Array" => 
      assert (gTypes.size == 1)
      "[" + gTypes(0).getShortName
    case _ => typeName
  }

  def addGenericType(newType: TypeInfo) = {
    gTypes = gTypes :+ newType
  }

  override def toString() = {
    var first = true
    var str = typeName
    if (gTypes.length > 0)
      str += "["
    gTypes.foreach(e => {
      if (first == false)
        str += ", "
      str += e.toString
      first = false
    })
    if (gTypes.length > 0)
      str += "]"
    str
  }
}

