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
package org.apache.j2fa
import java.util.logging._
import java.io._
import collection.JavaConversions._

import com.amd.aparapi.Config
import com.amd.aparapi.internal.writer.KernelWriter
import com.amd.aparapi.internal.writer.JParameter
import com.amd.aparapi.internal.writer.JParameter.DIRECTION

class TopKernelWriter(kernelList : List[Kernel]) {
    val logger = Logger.getLogger(Config.getLoggerName)

    if (logger.isLoggable(Level.FINEST)) {
        logger.finest("Subkernels: ")
        kernelList.foreach(k => logger.finest("-> " + k.getKernelName))
    }

    // Create a string builder for writing the kernel
    val sb = new StringBuilder

    def writeToString(): String = {
        // Write headers
        writeln("#include <string.h>")

        // Write subkernels
        kernelList.foreach(k => {
            writeln("// ============")
            writeln("// Kernel " + k.getKernelName + " Start")
            writeln("// ============")
            write(k.getKernel) // FIXME: Subfunction name conflict
            writeln("// ============")
            writeln("// Kernel " + k.getKernelName + " End")
            writeln("// ============")        
        })

        // Write the top kernel
        writeln()
        write("void kernel_top(int N")

        // Take input arguments from the first kernel
        val firstKernelArgs = asScalaBuffer(kernelList.head.getArgs)
        firstKernelArgs.foreach(arg => {
            if (arg.getDir == JParameter.DIRECTION.IN)
                write(",\n\t" + arg.getCType + "* " + arg.getName)
        })

        // Take the output arguments from the last kernel
        val lastKernelArgs = asScalaBuffer(kernelList.last.getArgs)
        lastKernelArgs.foreach(arg => {
            if (arg.getDir == JParameter.DIRECTION.OUT)
                write(",\n\t" + arg.getCType + "* " + arg.getName)
        })

        // Broadcast arguments
        var refArgs = Set[JParameter]()
        kernelList.foreach(k => {
            val refArgSet = asScalaBuffer(k.getRefArgs).toSet
            refArgs = refArgs | refArgSet
            logger.info("total " + refArgs.size + " refs")
        })
        refArgs.foreach(arg => {
            write(",\n\t" + arg.getCType + " " + arg.getName)
        })

        writeln(") {")

        kernelList.foreach(kernel => {
            write(kernel.getKernelName + "(")
            writeln(");")
        })

        writeln("}")
        commitToString
    }

    def commitToString(): String = {
        val str = sb.toString
        KernelWriter.postProcforHLS(str)
    }

    def writeln(): Unit = writeln("")

    def writeln(_string: String): Unit = {
        write(_string + "\n")
    }

    def write(_string: String): Unit = {
        sb.append(_string)
    }
}