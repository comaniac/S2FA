package org.apache.spark.blaze

import scala.reflect.ClassTag
import scala.io._
import scala.sys.process._

import java.util.LinkedList

import com.amd.aparapi.internal.model.Entrypoint
import com.amd.aparapi.internal.model.ClassModel
import com.amd.aparapi.internal.writer.BlockWriter._
import com.amd.aparapi.internal.writer.BlockWriter.ScalaParameter
import com.amd.aparapi.internal.writer.BlockWriter.ScalaParameter.DIRECTION

object CodeGenUtil {

  def isPrimitive(typeString : String) : Boolean = {
    return  typeString.equals("I") || 
            typeString.equals("D") || 
            typeString.equals("F") || 
            typeString.equals("J")
  }

  def getPrimitiveTypeForDescriptor(descString : String) : String = {
    if (descString.equals("I")) {
      return "int"
    } else if (descString.equals("D")) {
      return "double"
    } else if (descString.equals("F")) {
      return "float"
    } else if (descString.equals("J")) {
      return "long"
    } else {
      return null
    }
  }

  def getClassForDescriptor(descString : String) : Class[_] = {
    if (isPrimitive(descString)) {
      return null
    }

    var className : String = getTypeForDescriptor(descString)
    return Class.forName(className.trim)
  }

  def getTypeForDescriptor(descString : String) : String = {
    var primitive : String = getPrimitiveTypeForDescriptor(descString)
    if (primitive == null) {
      primitive = ClassModel.convert(descString, "", true)
    }
    primitive
  }

  def getParamObjsFromMethodDescriptor(descriptor : String,
      expectedNumParams : Int) : (LinkedList[ScalaArrayParameter], Int) = {
    val arguments : String = descriptor.substring(descriptor.indexOf('(') + 1,
        descriptor.lastIndexOf(')'))
    val argumentsArr : Array[String] = arguments.split(",")
    var maxArrayDim: Int = 0

    assert(argumentsArr.length == expectedNumParams)

    // Check and parse array arguments.
    val argsArrWithDim = argumentsArr.map(arg => {
      val parsedArg = arg.replace("[", "")
      val arrayDim = arg.length - parsedArg.length
      if (arrayDim > maxArrayDim)
        maxArrayDim = arrayDim
      (parsedArg, arrayDim)
    })

    val params = new LinkedList[ScalaArrayParameter]()

    for (i <- 0 until argumentsArr.length) {
      val argumentDesc : String = argsArrWithDim(i)._1

      if (argsArrWithDim(i)._2 == 0)
        params.add(new ScalaArrayParameter(getTypeForDescriptor(argumentDesc),
              getClassForDescriptor(argumentDesc), "in" + i, DIRECTION.IN))
      else
        params.add(new ScalaArrayParameter(getTypeForDescriptor(argumentDesc),
              getClassForDescriptor(argumentDesc), "ary_in" + i, DIRECTION.IN))
    }

    (params, maxArrayDim)
  }

  def getReturnObjsFromMethodDescriptor(descriptor : String) : (ScalaArrayParameter, Int) = {
    val returnType : String = descriptor.substring(
        descriptor.lastIndexOf(')') + 1)
    val parsedType = returnType.replace("[", "")
    val dim = returnType.length - parsedType.length
    val param = 
      if (dim == 0) 
        new ScalaArrayParameter(getTypeForDescriptor(parsedType),
            getClassForDescriptor(parsedType), "out", DIRECTION.OUT)
      else
        new ScalaArrayParameter(getTypeForDescriptor(parsedType),
            getClassForDescriptor(parsedType), "ary_out", DIRECTION.OUT)
       
    (param, dim)
  }

  def cleanClassName(className : String) : String = {
    if (className.length() == 1) {
      // Primitive descriptor
      return className
    } else if (className.equals("java.lang.Integer")) {
      return "I"
    } else if (className.equals("java.lang.Float")) {
      return "F"
    } else if (className.equals("java.lang.Double")) {
      return "D"
    } else {
      return "L" + className + ";"
    }
  }

  def applyBoko(kernelPath : String) = {
    val bokoExe = sys.env("BLAZE_HOME") + "/boko/bin/Boko"
    val err = new StringBuilder
    val recorder = ProcessLogger((e: String) => err.append(e))

    Process(bokoExe + " " + kernelPath + " -- ") ! recorder
    err.toString
  }
}
