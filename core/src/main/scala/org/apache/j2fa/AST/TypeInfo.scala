package org.apache.j2fa.AST

import scala.reflect.runtime.universe._

import org.apache.j2fa._

class TypeInfo(var typeName: String) {
  var gTypes: List[TypeInfo] = List[TypeInfo]()
//  if (typeName != null && typeName.equals("Vector"))
//    typeName = "org/apache/spark/mllib/linalg/Vector"

  def this() = {
    this(null)
  }

  def setName(name: String) = {
    typeName = name
//    if (typeName.equals("Vector"))
//      typeName = "org/apache/spark/mllib/linalg/Vector"
  }

  def getName = typeName

  def getShortName : String = {
    val shortTypeName = typeName.substring(typeName.lastIndexOf('.') + 1)
    shortTypeName match {
    case "Array" => 
      assert (gTypes.size == 1)
      "[" + gTypes(0).getShortName

    case _ =>
      Utils.asBytecodeType(typeName.replace("scala.", ""))
    }
  }

  def getFullType : String = {
    var first = true
    var str = getShortName
    val hasGenericTypes = 
      if (typeName.equals("Array") == false && gTypes.length > 0) 
        true
      else
        false

    if (hasGenericTypes) {
      str = str.substring(0, str.length - 1)
      str += "<"

      gTypes.foreach(e => {
        if (first == false)
          str += ","
        str += e.getShortName
        first = false
      })
      str += ">;"
    }
    str
  }

  def addGenericType(newType: TypeInfo) = {
    gTypes = gTypes :+ newType
  }

  override def toString() = {
    var first = true
    var str = typeName
    if (gTypes.length > 0)
      str += "<"
    gTypes.foreach(e => {
      if (first == false)
        str += ", "
      str += e.toString
      first = false
    })
    if (gTypes.length > 0)
      str += ">"
    str
  }
}

