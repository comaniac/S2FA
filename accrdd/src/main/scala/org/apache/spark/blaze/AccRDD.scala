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
import java.util.LinkedList
import java.util.ArrayList     
import java.nio.ByteBuffer     
import java.nio.ByteOrder      

import scala.reflect.{ClassTag, classTag}
import scala.reflect.runtime.universe._

import org.apache.spark._
import org.apache.spark.{Partition, TaskContext}
import org.apache.spark.rdd._
import org.apache.spark.storage._
import org.apache.spark.scheduler._

import com.amd.aparapi.internal.model.ClassModel
import com.amd.aparapi.internal.model.Tuple2ClassModel
import com.amd.aparapi.internal.model.HardCodedClassModels
import com.amd.aparapi.internal.model.HardCodedClassModels.ShouldNotCallMatcher
import com.amd.aparapi.internal.model.Entrypoint
import com.amd.aparapi.internal.writer.KernelWriter
import com.amd.aparapi.internal.writer.KernelWriter.WriterAndKernel
import com.amd.aparapi.internal.writer.HostWriter
import com.amd.aparapi.internal.writer.HostWriter.WriterAndHost
import com.amd.aparapi.internal.writer.BlockWriter.ScalaArrayParameter
import com.amd.aparapi.internal.writer.BlockWriter.ScalaParameter.DIRECTION

/**
  * A RDD that uses accelerator to accelerate the computation. The behavior of AccRDD is 
  * similar to Spark partition RDD which performs the computation for a whole partition at a
  * time. AccRDD also processes a partition to reduce the communication overhead.
  *
  * @param appId The unique application ID of Spark.
  * @param prev The original RDD of Spark.
  * @param acc The developer extended accelerator class.
  */
class AccRDD[U: ClassTag, T: ClassTag](appId: Int, prev: RDD[T], acc: Accelerator[T, U]) 
  extends RDD[U](prev) with Logging {

  var entryPoint : Entrypoint = null

  def getPrevRDD() = prev
  def getRDD() = this

  override def getPartitions: Array[Partition] = firstParent[T].partitions

  override def compute(split: Partition, context: TaskContext) = {
    val numBlock: Int = 1 // Now we just use 1 block for an input partition.

    val blockId = new Array[Long](numBlock)
    val brdcstIdOrValue = new Array[(Long, Boolean)](acc.getArgNum)

    // Generate an input data block ID array
    for (j <- 0 until numBlock) {
      blockId(j) = Util.getBlockID(appId, getPrevRDD.id, split.index, j)
    }

    val isCached = inMemoryCheck(split)

    val resultIter = new Iterator[U] {
      var outputIter: Iterator[U] = null
      var outputAry: Array[U] = null // Length is unknown before reading the input
      var dataLength: Int = -1
      val typeSize: Int = Util.getTypeSizeByRDD(getRDD())

      var startTime: Long = 0
      var elapseTime: Long = 0

      try {
        if (typeSize == -1)
          throw new RuntimeException("Cannot recognize RDD data type")

        if (split.index == 0) // FIXME: Testing
          genOpenCLKernel(acc.id)

        // Get broadcast block IDs
        for (j <- 0 until brdcstIdOrValue.length) {
          val arg = acc.getArg(j)
          if (arg.isDefined == false)
            throw new RuntimeException("Argument index " + j + " is out of range.")

          // Set as true if it is the ID
          if (arg.get.isInstanceOf[Long])
            brdcstIdOrValue(j) = (arg.get.asInstanceOf[Long], false) 
          else 
            brdcstIdOrValue(j) = ((arg.get.asInstanceOf[BlazeBroadcast[_]]).brdcst_id, true)
        }

        val transmitter = new DataTransmitter()
        if (transmitter.isConnect == false)
          throw new RuntimeException("Connection refuse.")
        var msg = DataTransmitter.buildRequest(acc.id, blockId, brdcstIdOrValue.unzip._1.toArray, 
          brdcstIdOrValue.unzip._2.toArray)

        startTime = System.nanoTime
        transmitter.send(msg)
        val revMsg = transmitter.receive()
        elapseTime = System.nanoTime - startTime
        logInfo("Partition " + split.index + " communication latency: " + elapseTime + " ns")

        if (revMsg.getType() != AccMessage.MsgType.ACCGRANT) {
          // TODO: Manager should return an error code
          if (split.index == 0) { // Only let one worker to generate the kernel
            logInfo("Generating the OpenCL kernel")
            val thread = new Thread {
              override def run {
                genOpenCLKernel(acc.id)
              }
            }
            thread.start
          }
          throw new RuntimeException("Request reject.")
        }

        startTime = System.nanoTime

        val dataMsg = DataTransmitter.buildMessage(AccMessage.MsgType.ACCDATA)

        // Prepare input data blocks
        var requireData: Boolean = false
        for (i <- 0 until numBlock) {
          if (!revMsg.getData(i).getCached()) { // Send data block if Blaze manager hasn't cached it.
            requireData = true

            // The data has been read and cached by Spark, serialize it and send memory mapped file path.
            if (isCached == true || !split.isInstanceOf[HadoopPartition]) {
              // Issue #26: This partition might be a CoalescedRDDPartition, 
              // which has no file information so we have to load it in advance.

              val inputAry: Array[T] = (firstParent[T].iterator(split, context)).toArray
              val mappedFileInfo = Util.serializePartition(appId, inputAry, blockId(i))
              dataLength = dataLength + mappedFileInfo._2 // We know element # by reading the file
              DataTransmitter.addData(dataMsg, blockId(i), mappedFileInfo._2, mappedFileInfo._3,
                  mappedFileInfo._2 * typeSize, 0, mappedFileInfo._1)
            }
            else { // The data hasn't been read by Spark, send HDFS file path directly (data length is unknown)
              val splitInfo: String = split.asInstanceOf[HadoopPartition].inputSplit.toString

              // Parse Hadoop file string: file:<path>:<offset>+<size>
              val filePath: String = splitInfo.substring(
                  splitInfo.indexOf(':') + 1, splitInfo.lastIndexOf(':'))
              val fileOffset: Int = splitInfo.substring(
                  splitInfo.lastIndexOf(':') + 1, splitInfo.lastIndexOf('+')).toInt
              val fileSize: Int = splitInfo.substring(
                  splitInfo.lastIndexOf('+') + 1, splitInfo.length).toInt
           
              DataTransmitter.addData(dataMsg, blockId(i), -1, 1,
                  fileSize, fileOffset, filePath)
            }
          }
        }

        // Prepare broadcast blocks
        var numBrdcstBlock: Int = 0
        for (i <- 0 until brdcstIdOrValue.length) {
          if (brdcstIdOrValue(i)._2 == true && !revMsg.getData(numBrdcstBlock + numBlock).getCached()) {
            requireData = true
            val bcData = (acc.getArg(i).get.asInstanceOf[BlazeBroadcast[_]]).value // Uncached data must be BlazeBroadcast.
            if (bcData.getClass.isArray) { // Serialize array and use memory mapped file to send the data.
              val arrayData = bcData.asInstanceOf[Array[_]]
              val mappedFileInfo = Util.serializePartition(appId, arrayData, brdcstIdOrValue(i)._1)
              val typeName = arrayData(0).getClass.getName.replace("java.lang.", "").toLowerCase
              val typeSize = Util.getTypeSizeByName(typeName)
              assert(typeSize != 0, "Cannot find the size of type " + typeName)

              DataTransmitter.addData(dataMsg, brdcstIdOrValue(i)._1, arrayData.length, 1,
                arrayData.length * typeSize, 0, mappedFileInfo._1)
              (acc.getArg(i).get.asInstanceOf[BlazeBroadcast[_]]).length = arrayData.length
              (acc.getArg(i).get.asInstanceOf[BlazeBroadcast[_]]).size = arrayData.length * typeSize
            }
            else { // Send a scalar data through the socket directly.
              val longData: Long = Util.casting(bcData, classOf[Long])
              DataTransmitter.addScalarData(dataMsg, brdcstIdOrValue(i)._1, longData)
              (acc.getArg(i).get.asInstanceOf[BlazeBroadcast[_]]).length = 1
              (acc.getArg(i).get.asInstanceOf[BlazeBroadcast[_]]).size = 4
            }
            numBrdcstBlock += 1
          }
        }

        elapseTime = System.nanoTime - startTime
        logInfo("Partition " + split.index + " preprocesses time: " + elapseTime + " ns");

        // Send ACCDATA message only when it is required.
        if (requireData == true) {
          logInfo(Util.logMsg(dataMsg))
          transmitter.send(dataMsg)
        }

        val finalRevMsg = transmitter.receive()
        logInfo(Util.logMsg(finalRevMsg))

        if (finalRevMsg.getType() == AccMessage.MsgType.ACCFINISH) {
          var numItems: Int = 0
          val blkLength = new Array[Int](numBlock)
          val itemLength = new Array[Int](numBlock)

          // First read: Compute the correct number of output items.
          for (i <- 0 until numBlock) {
            blkLength(i) = finalRevMsg.getData(i).getLength()
            if (finalRevMsg.getData(i).hasNumItems()) {
              itemLength(i) = blkLength(i) / finalRevMsg.getData(i).getNumItems()
            }
            else {
              itemLength(i) = 1
            }
            numItems += blkLength(i) / itemLength(i)
          }
          if (numItems == 0)
            throw new RuntimeException("Manager returns an invalid data length")

          outputAry = new Array[U](numItems)

          startTime = System.nanoTime
          
          // Second read: Read outputs from memory mapped file.
          var idx = 0
          for (i <- 0 until numBlock) { // Concatenate all blocks (currently only 1 block)

            Util.readMemoryMappedFile(
                outputAry,
                idx, 
                blkLength(i), itemLength(i), 
                finalRevMsg.getData(i).getPath())

            idx = idx + blkLength(i) / itemLength(i)
          }
          outputIter = outputAry.iterator

          elapseTime = System.nanoTime - startTime
          logInfo("Partition " + split.index + " reads memory mapped file: " + elapseTime + " ns")
        }
        else
          throw new RuntimeException("Task failed.")
      }
      catch {
        case e: Throwable =>
          val sw = new StringWriter
          e.printStackTrace(new PrintWriter(sw))
          logInfo("Partition " + split.index + " fails to be executed on accelerator: " + sw.toString)
          outputIter = computeOnJTP(split, context)
      }

      def hasNext(): Boolean = {
        outputIter.hasNext
      }

      def next(): U = {
        outputIter.next
      }
    }
    resultIter
  }

  /**
    * A method for the developer to execute the computation on the accelerator.
    * The original Spark API `map` is still available.
    *
    * @param clazz Extended accelerator class.
    * @return A transformed AccRDD.
    */
  def map_acc[V: ClassTag](clazz: Accelerator[U, V]): AccRDD[V, U] = {
    new AccRDD(appId, this, clazz)
  }

  /**
    * A method for the developer to execute the computation on the accelerator.
    * The original Spark API `mapPartition` is still available.
    *
    * @param clazz Extended accelerator class.
    * @return A transformed AccMapPartitionsRDD.
    */
  def mapPartitions_acc[V: ClassTag](clazz: Accelerator[U, V]): AccRDD[V, U] = {
    new AccMapPartitionsRDD(appId, this.asInstanceOf[RDD[U]], clazz)
  }

  /**
    * Consult Spark block manager to see if the partition is cached or not.
    *
    * @param split The partition of a RDD.
    * @return A boolean value to indicate if the partition is cached.
    */
  def inMemoryCheck(split: Partition): Boolean = { 
    val splitKey = RDDBlockId(getPrevRDD.id, split.index)
    val result = SparkEnv.get.blockManager.getStatus(splitKey)
    if (result.isDefined && result.get.isCached == true) {
      true
    }
    else {
      false
    }
  }

  /**
    * In case of failing to execute the computation on accelerator, 
    * Blaze will execute the computation on JVM instead to guarantee the
    * functionality correctness, even the overhead of failing to execute 
    * on accelerator is quite large.
    *
    * @param split The partition to be executed on JVM.
    * @param context TaskContext of Spark.
    * @return The output array
    */
  def computeOnJTP(split: Partition, context: TaskContext): Iterator[U] = {
    logInfo("Compute partition " + split.index + " using CPU")
    val inputAry: Array[T] = (firstParent[T].iterator(split, context)).toArray
    val dataLength = inputAry.length
    val outputAry = new Array[U](dataLength)
    var j: Int = 0
    while (j < inputAry.length) {
      outputAry(j) = acc.call(inputAry(j).asInstanceOf[T])
      j = j + 1
    }
    outputAry.iterator
  }

  def genOpenCLKernel(id: String) = {
    System.setProperty("com.amd.aparapi.enable.NEW", "true")
    val kernelPath : String = "/tmp/blaze_kernel_" + id + ".cl" 
    // TODO:  1. Should be a shared path e.g. HDFS
    //        2. Should check if the kernel is existed in advance

    if (new File(kernelPath).exists) {
      logWarning("Kernel exists, skip generating")
    }
    else {
      val classModel : ClassModel = ClassModel.createClassModel(acc.getClass, null, new ShouldNotCallMatcher())
      val hardCodedClassModels : HardCodedClassModels = new HardCodedClassModels()
      var isMapPartitions: Boolean = if (this.getClass.getName.contains("AccMapPartitionsRDD")) true else false
      var method =  if (!isMapPartitions) classModel.getPrimitiveCallMethod 
                    else classModel.getPrimitiveCallPartitionsMethod

      try {
//        if (isMapPartitions && method != null) { // FIXME
//          throw new RuntimeException("Currently we don't support MapPartitions")
//        }

        if (method == null)
          throw new RuntimeException("Cannot find available call method.")
        val descriptor : String = method.getDescriptor

        // Parse input type: Expect 1 input argument for map function.
        val paramsWithDim = CodeGenUtil.getParamObjsFromMethodDescriptor(descriptor, 1)
        if (paramsWithDim._2 == 1)
          logWarning("[CodeGen] Input argument is 1-D array. This may cause huge overhead since" +
            " data will be serialized during the runtime.")
        else if (paramsWithDim._2 > 1) {
          throw new RuntimeException("Multi-dimensional array cannot be an input argument." +
            " Stop generating OpenCL kernel.")
        }
        val params : LinkedList[ScalaArrayParameter] = paramsWithDim._1

        // Parse output type.
        val returnWithDim = CodeGenUtil.getReturnObjsFromMethodDescriptor(descriptor)
        if (returnWithDim._2 == 1)
          logWarning("[CodeGen] Output argument is 1-D array. This may cause huge overhead since" +
            " data will be deserialized during the runtime.")
        else if (returnWithDim._2 > 1) {
          throw new RuntimeException("Multi-dimensional array cannot be an output argument." +
            " Stop generating OpenCL kernel.")
        }
        params.add(returnWithDim._1)

        val fun: T => U = acc.call
        entryPoint = classModel.getEntrypoint("call", descriptor, fun, params, hardCodedClassModels)
        val writerAndKernel = KernelWriter.writeToString(entryPoint, params)
        val openCL = writerAndKernel.kernel
        val kernelFile = new PrintWriter(new File(kernelPath))
        kernelFile.write(KernelWriter.applyXilinxPatch(openCL))
        kernelFile.close
        val res = CodeGenUtil.applyBoko(kernelPath)
        logInfo("[CodeGen] Generate and optimize the kernel successfully")
        logWarning("[Boko] " + res)
      } catch {
        case e: Throwable =>
          val sw = new StringWriter
          e.printStackTrace(new PrintWriter(sw))
          logWarning("[CodeGen] OpenCL kernel generated failed: " + sw.toString)
      }
    }
  }
}

