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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode
                                                
class BlazeMemoryFileHandler(var data: Array[_]) {
  var fileName: String = null
  var eltNum: Int = 0
  var itemNum: Int = 0
  var typeName: String = null
  var typeSize: Int = -1

  /**
    * Serialize and write the data to memory mapped file.
    *
    * @param prefix The prefix of memory mapped file. Usually use application ID.
    * @param id The ID of the serialized data.
    */
  def serialization(prefix: Int, id: Long): Unit = {
    val filePath: String = System.getProperty("java.io.tmpdir") + "/" + prefix
    val isBrdcst: Boolean = if (id < 0) true else false
    val isArray: Boolean = data(0).isInstanceOf[Array[_]]

    val sampledData: Any = if (isArray) data(0).asInstanceOf[Array[_]](0) else data(0)
    val typeSize: Int = Util.getTypeSize(sampledData)
    if (typeSize == -1)
      throw new RuntimeException("Unsupported input data type.")

    val dataType: String = Util.getTypeName(sampledData)
    val itemNum: Int = data.length

    val fileName: String = if (isBrdcst) { 
      filePath + "_brdcst_" + (-id) + ".dat"
    } else {
      filePath + id + ".dat"
    }

    // Calculate buffer length
    var bufferLength = 0
    if (isArray) {
      for (e <- data) {
        val a = e.asInstanceOf[Array[_]]
        bufferLength = bufferLength + a.length
      }
    }
    else
      bufferLength = data.length

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

    for (e <- data) {
      if (isArray) {
        for (a <- e.asInstanceOf[Array[_]]) {
          dataType(0) match {
            case 'c' => buf.putChar(a.asInstanceOf[Char].charValue)
            case 'i' => buf.putInt(a.asInstanceOf[Int].intValue)
            case 'f' => buf.putFloat(a.asInstanceOf[Float].floatValue)
            case 'l' => buf.putLong(a.asInstanceOf[Long].longValue)
            case 'd' => buf.putDouble(a.asInstanceOf[Double].doubleValue)
            case _ =>
              throw new RuntimeException("Unsupported type " + dataType)
          }
        }
      }
      else {
        dataType(0) match {
          case 'c' => buf.putChar(e.asInstanceOf[Char].charValue)
          case 'i' => buf.putInt(e.asInstanceOf[Int].intValue)
          case 'f' => buf.putFloat(e.asInstanceOf[Float].floatValue)
          case 'l' => buf.putLong(e.asInstanceOf[Long].longValue)
          case 'd' => buf.putDouble(e.asInstanceOf[Double].doubleValue)
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

    this.fileName = fileName
    this.eltNum = bufferLength
    this.itemNum = itemNum
    this.typeSize = typeSize
    this.typeName = dataType
  }

  /**
    * Read and deserialize the data from memory mapped file.
    *
    * @param bufferLength The total element # of output.
    * @param itemLength The length of a item. If not 1, means array type.
    * @param fileName File name of memory mapped file.
    */
  def readMemoryMappedFile (
      sample: Any,
      bufferLength: Int,
      itemLength: Int,
      fileName: String): Unit = {

    require(sample != null || data(0) != null)

    // Fetch size information
    val mySample: Any = if (sample != null) sample else data(0)
    val isArray: Boolean = mySample.isInstanceOf[Array[_]]
    val sampledData: Any = if (isArray) mySample.asInstanceOf[Array[_]](0) else mySample
    val typeSize: Int = Util.getTypeSize(sampledData)
    val dataType: String = Util.getTypeName(sampledData)

    // Create and write memory mapped file
    var raf: RandomAccessFile = null

    try {
      if (typeSize == -1)
        throw new RuntimeException("Unsupported type " + dataType)

      raf = new RandomAccessFile(fileName, "r")
    } catch {
      case e: IOException =>
        throw new IOException("Fail to read memory mapped file " + fileName + ": " + e.toString)
    }

    val fc: FileChannel = raf.getChannel()
    val buf: ByteBuffer = fc.map(MapMode.READ_ONLY, 0, bufferLength * typeSize)
    buf.order(ByteOrder.LITTLE_ENDIAN)

    for (idx <- 0 until bufferLength / itemLength) {
      if (isArray) {
        for (ii <- 0 until itemLength) {
          dataType(0) match {
            case 'c' => data(idx).asInstanceOf[Array[Char]](ii) = buf.getChar()
            case 'i' => data(idx).asInstanceOf[Array[Int]](ii) = buf.getInt()
            case 'f' => data(idx).asInstanceOf[Array[Float]](ii) = buf.getFloat()
            case 'l' => data(idx).asInstanceOf[Array[Long]](ii) = buf.getLong()
            case 'd' => data(idx).asInstanceOf[Array[Double]](ii) = buf.getDouble()
            case _ =>
              throw new RuntimeException("Unsupported type " + dataType)
          }
        }
      }
      else {
         dataType(0) match {
          case 'c' => data.asInstanceOf[Array[Char]](idx) = buf.getChar()
          case 'i' => data.asInstanceOf[Array[Int]](idx) = buf.getInt()
          case 'f' => data.asInstanceOf[Array[Float]](idx) = buf.getFloat()
          case 'l' => data.asInstanceOf[Array[Long]](idx) = buf.getLong()
          case 'd' => data.asInstanceOf[Array[Double]](idx) = buf.getDouble()
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
    this.fileName = fileName
    this.typeSize = typeSize
    this.eltNum = bufferLength
    this.itemNum = bufferLength / itemLength
    this.typeName = dataType
  }
}
