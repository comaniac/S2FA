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

    // Indent level for code formatting
    val indent_pat = "  "
    var indent = 0
    var write_indent = true

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
        write("void kernel_top(int s2fa_total_task_num")

        // Take input arguments from the first kernel
        val firstKernelArgs = asScalaBuffer(kernelList.head.getArgs)
        firstKernelArgs.foreach(arg => {
            if (arg.getDir == JParameter.DIRECTION.IN)
                write(",\n" + indent_pat + arg.getCType + "* " + 
                    kernelList.head.getKernelName + "_" + arg.getName)
        })

        // Take the output arguments from the last kernel
        val lastKernelArgs = asScalaBuffer(kernelList.last.getArgs)
        lastKernelArgs.foreach(arg => {
            if (arg.getDir == JParameter.DIRECTION.OUT)
                write(",\n" + indent_pat + arg.getCType + "* " + 
                    kernelList.last.getKernelName + "_" + arg.getName)
        })

        // Broadcast arguments
        var refArgs = Set[JParameter]()
        kernelList.foreach(k => {
            val refArgSet = asScalaBuffer(k.getRefArgs).toSet
            refArgs = refArgs | refArgSet
        })
        refArgs.foreach(arg => {
            write(",\n" + indent_pat + arg.getCType + " " + arg.getName)
        })

        writeln(") {")
        in()

        // Function body

        // Inter-kernel buffer declaration 
        // Argunment format for kernels: (taskNum, input, output, reference)
        kernelList.view.zipWithIndex.foreach({case(k, idx) => 
            if (idx != 0) { // The first kernel inputs are top kernel arguments
                asScalaBuffer(k.getArgs).foreach(arg => {
                    if (arg.getDir == JParameter.DIRECTION.IN) {
                        writeln(arg.getCType + " " + k.getKernelName + 
                            "_" + arg.getName + "[];")
                        // FIXME: Array size
                    }
                })
            }
        })

        // Batch size declaration
        // FIXME         

        // Outermost loop for tasks
        writeln("for (int task = 0; task < s2fa_total_task_num; task += global_batch_size) {")
        in()  

        // Kernel function calls
        kernelList.view.zipWithIndex.foreach({case(kernel, idx) => 
            write(kernel.getKernelName + "(")
            write("global_batch_size") // Batch size (total task number )

            // Input
            asScalaBuffer(kernel.getArgs).foreach(arg => {
                if (arg.getDir == JParameter.DIRECTION.IN)
                    write(", " + kernel.getKernelName + "_" + arg.getName)
            })

            // Output
            if (idx == kernelList.size - 1) {
                // Outputs of the last kernel are arguments
                asScalaBuffer(kernel.getArgs).foreach(arg => {
                    if (arg.getDir == JParameter.DIRECTION.OUT)
                        write(", " + kernel.getKernelName + "_" + arg.getName)
                })
            }
            else {
                // Outputs of other kernels are inputs of the next kernel
                val nextKernel = kernelList(idx + 1)
                asScalaBuffer(nextKernel.getArgs).foreach(arg => {
                    if (arg.getDir == JParameter.DIRECTION.IN)
                        write(", " + nextKernel.getKernelName + "_" + arg.getName)
                })                
            }

            // Reference
            asScalaBuffer(kernel.getRefArgs).foreach(arg => {
                write(", " + arg.getName)
            })

            writeln(");")
        })

        out()
        writeln("}")
        out()        
        writeln("}")
        commitToString
    }

    def in() = {
        indent += 1
    }

    def out() = {
        indent = if (indent > 0) indent - 1 else indent
    }

    def commitToString(): String = {
        val str = sb.toString
        KernelWriter.postProcforHLS(str)
    }

    def writeln(): Unit = writeln("")

    def writeln(_string: String): Unit = {
        write(_string + "\n")
        write_indent = true
    }

    def write(_string: String): Unit = {
        if (write_indent) {
            1 to indent foreach(_ => sb.append("  "))
            write_indent = false
        }
        sb.append(_string)
    }
}