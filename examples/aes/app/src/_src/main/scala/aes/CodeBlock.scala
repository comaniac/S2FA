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

import java.io._
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.channels.FileChannel.MapMode

import scala.reflect.ClassTag

import org.apache.spark.blaze._
import org.apache.spark.blaze.datablock._

/**
  * PointBlock wraps a partition of RDD[Code]
  */
class CodeBlock(
    var id: Long, 
    var typeClazz: bzClass
  ) extends DataBlock[Code](id, typeClazz) {

  val typeName = typeClazz.toString
  lazy val pData = this.data
 
  override def getTypeSize = 64

  override def getPrimitiveData = pData

  override def serialize: Unit = {
    val buf = openBuffer.asIntBuffer
    this.data.foreach(e => {
      buf.put(e.data.asInstanceOf[Array[Int]])
    })
    closeBuffer
  }

  override def deserialize: Unit = {
    val flatData = new Array[Int](numElt * 16)
    val buf = openBuffer.asIntBuffer

    buf.get(this.data.asInstanceOf[Array[Int]], 0, numElt * 16)
    this.data = flatData.grouped(16).map(e => new Code(e)).toArray
    closeBuffer
  }
}
