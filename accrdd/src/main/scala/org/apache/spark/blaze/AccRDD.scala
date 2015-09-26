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

import ModeledType._

import java.io._
import java.util.LinkedList
import java.util.ArrayList     
import java.nio.ByteBuffer     
import java.nio.ByteOrder      

import scala.reflect.{ClassTag, classTag}
import scala.reflect.runtime.universe._
import scala.collection.mutable._

import org.apache.spark._
import org.apache.spark.{Partition, TaskContext}
import org.apache.spark.rdd._
import org.apache.spark.storage._
import org.apache.spark.scheduler._
import org.apache.spark.util.random._

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
class AccRDD[U: ClassTag, T: ClassTag](
  appId: Int, 
  prev: RDD[T], 
  acc: Accelerator[T, U],
  sampler: RandomSampler[T, T]
) extends RDD[U](prev) with Logging {

  var entryPoint : Entrypoint = null

  def getPrevRDD() = prev
  def getRDD() = this

  override def getPartitions: Array[Partition] = firstParent[T].partitions

  override def compute(split: Partition, context: TaskContext) = {
    val brdcstIdOrValue = new Array[(Long, Boolean)](acc.getArgNum)

    // Generate an input data block ID array.
    // (only Tuple types cause multiple sub-blocks. Now we support to Tuple2)
    val numBlock: Int = Util.getBlockNum(getRDD.asInstanceOf[RDD[T]])
    val blockId = new Array[Long](numBlock)  
    for (j <- 0 until numBlock) {
      blockId(j) = Util.getBlockID(appId, getPrevRDD.id, split.index, j)
    }

    // Followed by a mask ID
    val maskId: Long = Util.getBlockID(appId, getPrevRDD.id, split.index, numBlock)

    val isCached = inMemoryCheck(split)

    val resultIter = new Iterator[U] {
      var outputIter: Iterator[U] = null
      var outputAry: Array[U] = null // Length is unknown before reading the input
      var dataLength: Int = -1
      val isPrimitiveType: Boolean = Util.isPrimitiveTypeRDD(getRDD.asInstanceOf[RDD[T]])
      val modeledType: ModeledType = Util.whichModeledTypeRDD(getRDD.asInstanceOf[RDD[T]])
      var partitionMask: Array[Char] = null
      var isSampled: Boolean = false

      var startTime: Long = 0
      var elapseTime: Long = 0

      try {
        if (!isPrimitiveType && modeledType == ModeledType.NotModeled)
          throw new RuntimeException("RDD data type is not supported")

        if (split.index == 0) { // FIXME: Testing
          if (isPrimitiveType)
            genOpenCLKernel(acc.id)
          else { // Non primitive types must provide a sample object.
            val sample = firstParent[T].iterator(split, context).next
            genOpenCLKernel(acc.id, modeledType, Some(sample))
          }
        }

        // Get broadcast block IDs
        for (j <- 0 until brdcstIdOrValue.length) {

          // a tuple of (val, isID)
          brdcstIdOrValue(j) = acc.getArg(j) match {
            case Some(v: BlazeBroadcast[_]) => (v.brdcst_id, true)
            case Some(v: Long) => (Util.casting(v, classOf[Long]), false)
            case Some(v: Int) => (Util.casting(v, classOf[Long]), false)
            case Some(v: Double) => (Util.casting(v, classOf[Long]), false)
            case Some(v: Float) => (Util.casting(v, classOf[Long]), false)
            case _ => throw new RuntimeException("Invalid Broadcast arguement "+j)
          }
        }

        // Sample data if necessary (all sub-blocks use the same mask)
        if (sampler != null && partitionMask == null) {
          partitionMask = samplePartition(split, context)
          isSampled = true
        }

        val transmitter = new DataTransmitter()
        if (transmitter.isConnect == false)
          throw new RuntimeException("Connection refuse.")
        var msg = DataTransmitter.buildRequest(acc.id, blockId, isSampled, 
          brdcstIdOrValue.unzip._1.toArray, brdcstIdOrValue.unzip._2.toArray)

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

        val maskFileInfo: BlazeMemoryFileHandler = if (isSampled) {
          val handler = new BlazeMemoryFileHandler(partitionMask)
          handler.serialization(appId, maskId) 
          handler
        } else { 
          null
        }

        for (i <- 0 until numBlock) {
          if (!revMsg.getData(i).getCached()) { // Send data block if Blaze manager hasn't cached it.
            requireData = true

            // Serialize it and send memory mapped file path if:
            // 1. The data has been read and cached by Spark.
            // 2. The data is not a HadoopPartition which we cannot process without reading it first. (Issue #26)
            // 3. The type is not primitive so that we have to read it first for detail information.
            if (isCached == true || !split.isInstanceOf[HadoopPartition] || !isPrimitiveType) {
              val inputAry: Array[T] = (firstParent[T].iterator(split, context)).toArray

              // Get real input array considering Tuple types
              val subInputAry = if (numBlock == 1) inputAry else {
                i match {
                  case 0 => inputAry.asInstanceOf[Array[Tuple2[_,_]]].map(e => e._1)
                  case 1 => inputAry.asInstanceOf[Array[Tuple2[_,_]]].map(e => e._2)
                }
              }
              val mappedFileInfo = new BlazeMemoryFileHandler(subInputAry)
              mappedFileInfo.serialization(appId, blockId(i))

              dataLength = dataLength + mappedFileInfo.eltNum // We know element # by reading the file

              if (isSampled) {
                DataTransmitter.addData(dataMsg, blockId(i), mappedFileInfo.eltNum, mappedFileInfo.itemNum,
                    mappedFileInfo.eltNum * mappedFileInfo.typeSize, 0, mappedFileInfo.fileName, maskFileInfo.fileName)
              }
              else {
                DataTransmitter.addData(dataMsg, blockId(i), mappedFileInfo.eltNum, mappedFileInfo.itemNum,
                    mappedFileInfo.eltNum * mappedFileInfo.typeSize, 0, mappedFileInfo.fileName)
              }
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
          else if (revMsg.getData(i).getSampled()) {
            requireData = true
            DataTransmitter.addData(dataMsg, blockId(i), 0, maskFileInfo.itemNum, 0, 0, maskFileInfo.fileName)
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
              val mappedFileInfo = new BlazeMemoryFileHandler(arrayData)
              mappedFileInfo.serialization(appId, brdcstIdOrValue(i)._1)
              val typeName = mappedFileInfo.typeName
              val typeSize = mappedFileInfo.typeSize
              assert(typeSize != 0, "Cannot find the size of type " + typeName)

              DataTransmitter.addData(dataMsg, brdcstIdOrValue(i)._1, mappedFileInfo.eltNum, 1,
                mappedFileInfo.eltNum * typeSize, 0, mappedFileInfo.fileName)
              (acc.getArg(i).get.asInstanceOf[BlazeBroadcast[_]]).length = mappedFileInfo.eltNum
              (acc.getArg(i).get.asInstanceOf[BlazeBroadcast[_]]).size = mappedFileInfo.eltNum * typeSize
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
          val numOutputBlock: Int = finalRevMsg.getDataCount
          var numItems: Int = 0
          val blkLength = new Array[Int](numOutputBlock)
          val itemLength = new Array[Int](numOutputBlock)

          // First read: Compute the correct number of output items.
          for (i <- 0 until numOutputBlock) {
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

          // Allocate output array
          outputAry = new Array[U](numItems)
          if (outputAry.isInstanceOf[Array[Array[_]]]) {
            for (i <- 0 until numItems) {
              if (classTag[U] == classTag[Array[Int]])
                outputAry(i) = (new Array[Int](itemLength(i))).asInstanceOf[U]
              else if (classTag[U] == classTag[Array[Float]])
                outputAry(i) = (new Array[Float](itemLength(i))).asInstanceOf[U]
              else if (classTag[U] == classTag[Array[Long]])
                outputAry(i) = (new Array[Long](itemLength(i))).asInstanceOf[U]
              else if (classTag[U] == classTag[Array[Double]])
                outputAry(i) = (new Array[Double](itemLength(i))).asInstanceOf[U]
              else
                throw new RuntimeException("Unsupported output type.")
            }
          }

          startTime = System.nanoTime
          
          // Second read: Read outputs from memory mapped file.
          var idx = 0
          for (i <- 0 until numOutputBlock) { // Concatenate all blocks (currently only 1 block)

            val mappedFileInfo = new BlazeMemoryFileHandler(outputAry)
            mappedFileInfo.readMemoryMappedFile(
              idx, 
              blkLength(i), 
              itemLength(i), 
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
          outputIter = computeOnJTP(split, context, partitionMask)
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
    new AccRDD(appId, this, clazz, null)
  }

  /**
    * A method for the developer to execute the computation on the accelerator.
    * The original Spark API `mapPartition` is still available.
    *
    * @param clazz Extended accelerator class.
    * @return A transformed AccMapPartitionsRDD.
    */
  def mapPartitions_acc[V: ClassTag](clazz: Accelerator[U, V]): AccMapPartitionsRDD[V, U] = {
    new AccMapPartitionsRDD(appId, this.asInstanceOf[RDD[U]], clazz, null)
  }
  

  /**
    * A method for developer to sample a part of RDD.
    *
    * @param withReplacement 
    * @param fraction The fraction of sampled data.
    * @param seed The optinal value for developer to assign a random seed.
    * @return A RDD with a specific sampler.
    */
  def sample_acc(
    withReplacement: Boolean,
    fraction: Double,
    seed: Long = Util.random.nextLong): ShellRDD[T] = { 
    require(fraction >= 0.0, "Negative fraction value: " + fraction)

    var thisSampler: RandomSampler[T, T] = null

    if (withReplacement)
      thisSampler = new PoissonSampler[T](fraction)
    else
      thisSampler = new BernoulliSampler[T](fraction)
    thisSampler.setSeed(seed)

    new ShellRDD(appId, this.asInstanceOf[RDD[T]], thisSampler)
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

  def samplePartition(split: Partition, context: TaskContext): Array[Char] = {
    require(sampler != null)
    val thisSampler = sampler.clone
    val sampledIter = thisSampler.sample(firstParent[T].iterator(split, context))
    val inputIter = firstParent[T].iterator(split, context)
    val inputAry = inputIter.toArray
    var idx: Int = 0

    val mask = Array.fill[Char](inputAry.length)('0')

    while (sampledIter.hasNext) {
      val ii = inputAry.indexOf(sampledIter.next)
      require (ii != -1, "Sampled data doesn't match the original dataset!")
      mask(ii) = '1'
    }
    mask
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
  def computeOnJTP(split: Partition, context: TaskContext, partitionMask: Array[Char]): Iterator[U] = {
    logInfo("Compute partition " + split.index + " using CPU")
    val inputAry: Array[T] = (firstParent[T].iterator(split, context)).toArray
    val dataLength = inputAry.length
    var outputList = List[U]()

    if (partitionMask == null)
      logWarning("Partition " + split.index + " has no mask")

    var j: Int = 0
    while (j < inputAry.length) {
      if (partitionMask == null || partitionMask(j) != '0')
        outputList = outputList :+ acc.call(inputAry(j).asInstanceOf[T])
      j = j + 1
    }
    outputList.iterator
  }

  def genOpenCLKernel(
    id: String, 
    modeledType: ModeledType = NotModeled, 
    sample: Option[Any] = None
  ) = {
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

        if (modeledType == ModeledType.ScalaTuple2) {
          require (sample.isDefined)
          createHardCodedClassModel(sample.get.asInstanceOf[Tuple2[_,_]], 
            hardCodedClassModels, params.get(0))
          val sampledOut = acc.call(sample.get.asInstanceOf[T])
          if (sampledOut.isInstanceOf[Tuple2[_,_]]) {
            createHardCodedClassModel(sampledOut.asInstanceOf[Tuple2[_,_]], 
              hardCodedClassModels, params.get(1))
          }
        }

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

  def createHardCodedClassModel(
    obj: Tuple2[_, _],
    hardCodedClassModels: HardCodedClassModels, 
    param: ScalaArrayParameter
  ) {
    val inputClassType1 = obj._1.getClass
    val inputClassType2 = obj._2.getClass

    val inputClassType1Name = CodeGenUtil.cleanClassName(
        inputClassType1.getName)
    val inputClassType2Name = CodeGenUtil.cleanClassName(
        inputClassType2.getName)

    val tuple2ClassModel : Tuple2ClassModel = Tuple2ClassModel.create(
        inputClassType1Name, inputClassType2Name, param.getDir != DIRECTION.IN)
    hardCodedClassModels.addClassModelFor(obj.getClass, tuple2ClassModel)

    param.addTypeParameter(inputClassType1Name,
        !CodeGenUtil.isPrimitive(inputClassType1Name))
    param.addTypeParameter(inputClassType2Name,
        !CodeGenUtil.isPrimitive(inputClassType2Name))
  }
}

