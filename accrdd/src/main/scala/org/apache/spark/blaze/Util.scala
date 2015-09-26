/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.spark.blaze

import java.io._
import java.util.Calendar
import java.text.SimpleDateFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.Random

import scala.reflect.{ClassTag, classTag}
import scala.reflect.runtime.universe._         
import scala.collection.mutable        
import scala.collection.mutable.HashMap
                                                
import org.apache.spark._                       
import org.apache.spark.{Partition, TaskContext}
import org.apache.spark.rdd._                   

import ModeledType._

/**
  * Various utility methods used by Blaze.
  */
object Util {

  // The format of block IDs.
  // Format: | Broadcast, 1 | App ID, 32 | RDD ID, 18 | Partition ID, 12 | Sub-block ID, 1 |
  val APP_BIT_START = 31
  val RDD_BIT_START = 13
  val PARTITION_BIT_START = 1
  val BLOCK_BIT_START = 0

    /**
    * Check the type of the RDD is primitive or not.
    *
    * @param rdd RDD.
    * @return true for primitive type, false otherwise.
    */
  def isPrimitiveTypeRDD[T: ClassTag](rdd: RDD[T]): Boolean = {
    if ((classTag[T] == classTag[Byte] || classTag[T] == classTag[Array[Byte]])   ||
        (classTag[T] == classTag[Char] || classTag[T] == classTag[Array[Char]])   ||
        (classTag[T] == classTag[Short] || classTag[T] == classTag[Array[Short]]) ||  
        (classTag[T] == classTag[Int] || classTag[T] == classTag[Array[Int]])     ||
        (classTag[T] == classTag[Float] || classTag[T] == classTag[Array[Float]]) ||  
        (classTag[T] == classTag[Long] || classTag[T] == classTag[Array[Long]])   ||
        (classTag[T] == classTag[Double] || classTag[T] == classTag[Array[Double]])) 
      true
    else
      false
  }

   /**
    * Check the type of the RDD is modeled by Blaze or not.
    *
    * @param rdd RDD.
    * @return an enumeration for the modeled class.
    */
  def whichModeledTypeRDD[T: ClassTag](rdd: RDD[T]): ModeledType = {
    if (classTag[T] == classTag[Tuple2[_,_]])
      ModeledType.ScalaTuple2
    else
      ModeledType.NotModeled
  }
 

  def random = new Random()

  /**
    * Generate a string of message information.
    */
  def logMsg(msgBuilder: AccMessage.TaskMsg.Builder): String = {
    val msg = msgBuilder.build()
    var logStr = "Message: Type: "
    logStr = logStr + msg.getType() + ", Data: " + msg.getDataCount() + "\n"

    for (i <- 0 until msg.getDataCount()) {
      logStr = logStr + "Data " + i + ": "
      if (msg.getData(i).hasPartitionId())
        logStr = logStr + "ID: " + msg.getData(i).getPartitionId() + ", "
      if (msg.getData(i).hasLength())
        logStr = logStr + "Length: " + msg.getData(i).getLength() + ", "
      if (msg.getData(i).hasSize())
        logStr = logStr + "Size: " + msg.getData(i).getSize() + ", "
      if (msg.getData(i).hasNumItems())
        logStr = logStr + "NumItems: " + msg.getData(i).getNumItems() + ", "
      if (msg.getData(i).hasPath())
        logStr = logStr + "Path: " + msg.getData(i).getPath() + ", "
      if (msg.getData(i).hasMaskPath())
        logStr = logStr + "Mask_Path: " + msg.getData(i).getMaskPath()
      logStr = logStr + "\n"
    }
    logStr
  }

  /**
    * Generate a string of message information.
    */
  def logMsg(msg: AccMessage.TaskMsg): String = {
    var logStr = "Message: Type: "
    logStr = logStr + msg.getType() + ", Data: " + msg.getDataCount() + "\n"

    for (i <- 0 until msg.getDataCount()) {
      logStr = logStr + "Data " + i + ": "
      if (msg.getData(i).hasPartitionId())
        logStr = logStr + "ID: " + msg.getData(i).getPartitionId() + ", "
      if (msg.getData(i).hasLength())
        logStr = logStr + "Length: " + msg.getData(i).getLength() + ", "
      if (msg.getData(i).hasSize())
        logStr = logStr + "Size: " + msg.getData(i).getSize() + ", "
      if (msg.getData(i).hasPath())
        logStr = logStr + "Path: " + msg.getData(i).getPath()
      logStr = logStr + "\n"
    }
    logStr
  }

  /**
    * Consult the IP address by host name.
    * 
    * @param host Host name.
    * @return An IP address in string, or None if fails to find it.
    */
  def getIPByHostName(host: String): Option[String] = {
    try {
      val inetAddr = InetAddress.getByName(host)
      Some(inetAddr.getHostAddress)
    } catch {
      case e: UnknownHostException =>
        None
    }
  }

    /**
    * Calculate the number of blocks needed by the RDD.
    * (Now only support Tuple2)
    *
    * @param rdd RDD.
    * @return the number of blocks.
    */
  def getBlockNum[T: ClassTag](rdd: RDD[T]): Int = {
    if (classTag[T] == classTag[Tuple2[_, _]])
      2
    else
      1
  }

  /**
    * Get a type size of the input variable.
    *
    * @param in Input variable.
    * @return Type size of the variable in byte, return -1 for non-supported types.
    */
  def getTypeSize[T: ClassTag](in: T): Int = in match {
    case _: Byte =>   1
    case _: Char =>   2
    case _: Short =>  2
    case _: Int =>    4
    case _: Float =>  4
    case _: Long =>   8
    case _: Double => 8
    case _ =>        -1
  }

   /**
    * Get a type name initial of the input variable.
    *
    * @param in Input variable.
    * @return Type name initial of the variable in byte, 
    * return an empty string for non-supported types.
    */
  def getTypeName[T: ClassTag](in: T): String = in match {
    case _: Byte =>   "bype"
    case _: Char =>   "char"
    case _: Short =>  "short"
    case _: Int =>    "int"
    case _: Float =>  "float"
    case _: Long =>   "long"
    case _: Double => "double"
    case _: Any => "any"
    case _ =>         ""
  }
 
  /**
    * Get a primitive type size by type name.
    *
    * @param dataType Type name in string.
    * @return Type size in byte.
    */
  def getTypeSizeByName(dataType: String): Int = {
    var typeName = dataType
    if (dataType.length > 1)
      typeName = dataType(0).toString

    val typeSize: Int = typeName match {
      case "c" => 2
      case "i" => 4
      case "f" => 4
      case "l" => 8
      case "d" => 8
      case _ => -1
    }
    typeSize
  }
 
  /**
    * Casting a primitive scalar value to another primitive scalar value.
    *
    * @param value Input value (must be in primitive type).
    * @param clazz The type of the output value (must be in primitive type).
    * @return Casted value.
    */
  def casting[T: ClassTag, U: ClassTag](value: T, clazz: Class[U]): U = {
 	  val buf = ByteBuffer.allocate(8)
	  buf.order(ByteOrder.LITTLE_ENDIAN)

	  if (value.isInstanceOf[Int])
	    buf.putInt(value.asInstanceOf[Int])
    else if (value.isInstanceOf[Float])
	    buf.putFloat(value.asInstanceOf[Float])
    else if (value.isInstanceOf[Long])
	    buf.putLong(value.asInstanceOf[Long])
    else if (value.isInstanceOf[Double])
	    buf.putDouble(value.asInstanceOf[Double])
    else
      throw new RuntimeException("Unsupported casting type")

    if (clazz == classOf[Long])
  	  buf.getLong(0).asInstanceOf[U]
    else if (clazz == classOf[Int])
      buf.getInt(0).asInstanceOf[U]
    else if (clazz == classOf[Float])
      buf.getFloat(0).asInstanceOf[U]
    else if (clazz == classOf[Double])
      buf.getDouble(0).asInstanceOf[U]
    else
      throw new RuntimeException("Unsupported casting type")
  }

  /**
    * Generate a block ID by given information. There has two ID format for 
    * input and broadcast data blocks.
    * Input data: + | App ID, 32 | RDD ID, 18 | Partition ID, 12 | Sub-block ID, 1 |
    * Broadcast : - | App ID, 32 | Broadcast ID + 1, 31 |
    *
    * @param appId Application ID.
    * @param objId RDD or broadcast ID.
    * @param partitionIdx Partition index of a RDD. Do not specify for broadcast blocks.
    * @param blockIdx Sub-block index of a partition. Do not specify for broadcast blocks.
    * @return Block ID.
    */
  def getBlockID(appId: Int, objId: Int, partitionIdx: Int = -1, blockIdx: Int = -1): Long = {
    if (partitionIdx == -1) { // broadcast block
      -((appId.toLong << APP_BIT_START) + (objId + 1))
    }
    else { // input block
      (appId.toLong << APP_BIT_START) + (objId << RDD_BIT_START) + 
        (partitionIdx << PARTITION_BIT_START) + (blockIdx << BLOCK_BIT_START)
    }
  }

  /**
    * Serialize and write the data to memory mapped file.
    *
    * @param prefix The prefix of memory mapped file. Usually use application ID.
    * @param input The data to be serialized.
    * @param id The ID of the serialized data.
    * @return A triple pair of (file name, size, item number)
    */
  def serialization[T: ClassTag](prefix: Int, input: Array[T], id: Long): (String, Int, Int) = {
    var fileName: String = System.getProperty("java.io.tmpdir") + "/" + prefix
    if (id < 0) // Broadcast data
      fileName = fileName + "_brdcst_" + (-id) + ".dat"
    else // Partition data
      fileName = fileName + id + ".dat"

    val isArray: Boolean = input(0).isInstanceOf[Array[_]]
    val sampledData: Any = if (isArray) input(0).asInstanceOf[Array[_]](0) else input(0)
    val typeSize: Int = getTypeSize(sampledData)
    val dataType: String = getTypeName(sampledData)

    val itemNum: Int = input.length

    if (typeSize == -1)
      throw new RuntimeException("Unsupported input data type.")

    // Calculate buffer length
    var bufferLength = 0
    if (isArray) {
      for (e <- input) {
        val a = e.asInstanceOf[Array[_]]
        bufferLength = bufferLength + a.length
      }
    }
    else
      bufferLength = input.length

    // Create and write memory mapped file
    var raf: RandomAccessFile = null

    try {
      raf = new RandomAccessFile(fileName, "rw")
    } catch {
      case e: IOException =>
        throw new IOException("Fail to create memory mapped file " + fileName + ": " + e.toString)
    }
    val fc: FileChannel = raf.getChannel()
    val buf: ByteBuffer = fc.map(MapMode.READ_WRITE, 0, bufferLength * typeSize)
    buf.order(ByteOrder.LITTLE_ENDIAN)

    for (e <- input) {
      if (isArray) {
        for (a <- e.asInstanceOf[Array[_]]) {
          dataType match {
            case "c" => buf.putChar(a.asInstanceOf[Char].charValue)
            case "i" => buf.putInt(a.asInstanceOf[Int].intValue)
            case "f" => buf.putFloat(a.asInstanceOf[Float].floatValue)
            case "l" => buf.putLong(a.asInstanceOf[Long].longValue)
            case "d" => buf.putDouble(a.asInstanceOf[Double].doubleValue)
            case _ =>
              throw new RuntimeException("Unsupported type " + dataType)
          }
        }
      }
      else {
        dataType match {
          case "c" => buf.putChar(e.asInstanceOf[Char].charValue)
          case "i" => buf.putInt(e.asInstanceOf[Int].intValue)
          case "f" => buf.putFloat(e.asInstanceOf[Float].floatValue)
          case "l" => buf.putLong(e.asInstanceOf[Long].longValue)
          case "d" => buf.putDouble(e.asInstanceOf[Double].doubleValue)
          case _ =>
            throw new RuntimeException("Unsupported type " + dataType)
        }
      }
    }
   
    try {
      fc.close()
      raf.close()
    } catch {
      case e: IOException =>
        throw new IOException("Fail to close memory mapped file " + fileName + ": " + e.toString)
    }

    (fileName, bufferLength, itemNum)
  }

  /**
    * Deserialize the data from memory mapped file.
    *
    * @param out The array used for storing the deserialized data.
    * @param offset The offset of memory mapped file.
    * @param length The total length (total #element) of output.
    * @param itemLength The length (#element) of a item. If not 1, means array type.
    * @param fileName File name of memory mapped file.
    */
  def readMemoryMappedFile[T: ClassTag](
      out: Array[T], 
      offset: Int, 
      length: Int,
      itemLength: Int,
      fileName: String) = {

    if (out(offset) == null) {
      if (classTag[T] == classTag[Array[Int]])
        out(offset) = (new Array[Int](itemLength)).asInstanceOf[T]
      else if (classTag[T] == classTag[Array[Float]])
        out(offset) = (new Array[Float](itemLength)).asInstanceOf[T]
      else if (classTag[T] == classTag[Array[Long]])
        out(offset) = (new Array[Long](itemLength)).asInstanceOf[T]
      else if (classTag[T] == classTag[Array[Double]])
        out(offset) = (new Array[Double](itemLength)).asInstanceOf[T]
      else
        throw new RuntimeException("Unsupport type")
    }

    // Fetch size information
    val typeName = out(offset).getClass.getName.replace("java.lang.", "").toLowerCase()
    val dataType: String = typeName.replace("[", "")(0).toString // Only fetch the initial
    val typeSize: Int = getTypeSizeByName(dataType)

    val isArray: Boolean = typeName.contains("[")

    // Create and write memory mapped file
    var raf: RandomAccessFile = null

    try {
      if ((typeName.split('[').length - 1) > 1)
        throw new RuntimeException("Unsupport multi-dimension arrays: " + typeName)

      if (typeSize == -1)
        throw new RuntimeException("Unsupported type " + dataType)

      raf = new RandomAccessFile(fileName, "r")
    } catch {
      case e: IOException =>
        throw new IOException("Fail to read memory mapped file " + fileName + ": " + e.toString)
    }

    val fc: FileChannel = raf.getChannel()
    val buf: ByteBuffer = fc.map(MapMode.READ_ONLY, 0, length * typeSize)
    buf.order(ByteOrder.LITTLE_ENDIAN)

    for (idx <- offset until length/itemLength) {
      if (isArray) {
        for (ii <- 0 until itemLength) {
          dataType match {
            case "c" => out(idx).asInstanceOf[Array[Char]](ii) = buf.getChar()
            case "i" => out(idx).asInstanceOf[Array[Int]](ii) = buf.getInt()
            case "f" => out(idx).asInstanceOf[Array[Float]](ii) = buf.getFloat()
            case "l" => out(idx).asInstanceOf[Array[Long]](ii) = buf.getLong()
            case "d" => out(idx).asInstanceOf[Array[Double]](ii) = buf.getDouble()
            case _ =>
              throw new RuntimeException("Unsupported type " + dataType)
          }
        }
      }
      else {
         dataType match {
          case "c" => out(idx) = buf.getChar().asInstanceOf[T]
          case "i" => out(idx) = buf.getInt().asInstanceOf[T]
          case "f" => out(idx) = buf.getFloat().asInstanceOf[T]
          case "l" => out(idx) = buf.getLong().asInstanceOf[T]
          case "d" => out(idx) = buf.getDouble().asInstanceOf[T]
          case _ =>
            throw new RuntimeException("Unsupported type " + dataType)
        }
      }
    }
   
    try {
      fc.close
      raf.close

      // Issue #22: Delete memory mapped file after used.
      new File(fileName).delete
    } catch {
      case e: IOException =>
        throw new IOException("Fail to close/delete memory mapped file " + fileName + ": " + e.toString)
    }
  }
}
