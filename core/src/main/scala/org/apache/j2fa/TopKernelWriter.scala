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
import com.amd.aparapi.internal.writer.PrimitiveJParameter
import com.amd.aparapi.internal.writer.JParameter.DIRECTION

class TopKernelWriter(kernelList : List[Kernel]) {
    // FIXME: Enable dataflow with multiple functions in the future.
    require(kernelList.length == 1)

    val logger = Logger.getLogger(Config.getLoggerName)
    val total_task_num_str = "s2fa_total_task_num"
    val batch_task_num_str = total_task_num_str // FIXME

    if (logger.isLoggable(Level.FINEST)) {
        logger.finest("Subkernels: ")
        kernelList.foreach(k => logger.finest("-> " + k.getKernelName))
    }

    // Create a string builder for writing the kernel
    var sb: StringBuilder = null

    // Indent level for code formatting
    val indent_pat = "  "
    var indent = 0
    var write_indent = true

    def writeToString(): String = {
        sb = new StringBuilder
        // Write headers
        // FIXME: Now disable due to the ROSE bug
        // "error: cannot find stddef.h"
        // writeln("#include <string.h>")
        writeln("#include \"kernel_header.h\"")

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

// FIXME: Enable multiple kernels.
//        // Write the top kernel
//        writeln()
//        write("void kernel_top(int " + total_task_num_str)
//
//        // Take input arguments from the first kernel
//        val firstKernelArgs = asScalaBuffer(kernelList.head.getArgs)
//        firstKernelArgs.foreach(arg => {
//            if (arg.getDir == JParameter.DIRECTION.IN) {
//                val ctype = if (arg.getCType.contains("*")) arg.getCType
//                            else arg.getCType + "*"
//                write(",\n" + indent_pat + ctype + " " +
//                    kernelList.head.getKernelName + "_" + arg.getName)
//            }
//        })
//
//        // Take the output arguments from the last kernel
//        val lastKernelArgs = asScalaBuffer(kernelList.last.getArgs)
//        lastKernelArgs.foreach(arg => {
//            if (arg.getDir == JParameter.DIRECTION.OUT) {
//                val ctype = if (arg.getCType.contains("*")) arg.getCType
//                            else arg.getCType + "*"
//                write(",\n" + indent_pat + ctype + " " +
//                    kernelList.last.getKernelName + "_" + arg.getName)
//            }
//        })
//
//        // Broadcast arguments
//        var refArgs = Set[JParameter]()
//        kernelList.foreach(k => {
//            val refArgSet = asScalaBuffer(k.getRefArgs).toSet
//            refArgs = refArgs | refArgSet
//        })
//        refArgs.foreach(arg => {
//            write(",\n" + indent_pat + arg.getCType + " " + arg.getName)
//        })
//
//        writeln(") {")
//        in()
//
//        // Function body
//
//        // Inter-kernel buffer declaration
//        // Argunment format for kernels: (taskNum, input, output, reference)
//        kernelList.view.zipWithIndex.foreach({case(k, idx) =>
//            if (idx != 0) { // First kernel inputs are top kernel arguments
//                asScalaBuffer(k.getArgs).foreach(arg => {
//                    if (arg.getDir == JParameter.DIRECTION.IN) {
//                        writeln(arg.getCType + " " + k.getKernelName +
//                            "_" + arg.getName + "[];")
//                        // FIXME: Array size
//                    }
//                })
//            }
//        })
//
//        // Batch size declaration
//        // FIXME: Determine the batch size if we have multiple kernels.
//
//        // Outermost loop for tasks
//        writeln("for (int task = 0; task < " + total_task_num_str +
//          "; task += " + batch_task_num_str + ") {")
//        in()
//
//        // Kernel function calls
//        kernelList.view.zipWithIndex.foreach({case(kernel, idx) =>
//            write(kernel.getKernelName + "(")
//            // Batch size (total task number for one kernel case)
//            write(batch_task_num_str)
//
//            // Input
//            asScalaBuffer(kernel.getArgs).foreach(arg => {
//                if (arg.getDir == JParameter.DIRECTION.IN) {
//                    write(", " + kernel.getKernelName + "_" + arg.getName)
//                    if (arg.isArray && arg.isPrimitive) {
//                        write("[task * " +
//                          batch_task_num_str + " * " +
//                          arg.asInstanceOf[PrimitiveJParameter]
//                            .getItemLength() + "]")
//                    }
//                }
//            })
//
//            // Output
//            if (idx == kernelList.size - 1) {
//                // Outputs of the last kernel are arguments
//                asScalaBuffer(kernel.getArgs).foreach(arg => {
//                    if (arg.getDir == JParameter.DIRECTION.OUT) {
//                        write(", " + kernel.getKernelName + "_" + arg.getName)
//                        if (arg.isArray && arg.isPrimitive) {
//                            write("[task * " +
//                              batch_task_num_str + " * " +
//                              arg.asInstanceOf[PrimitiveJParameter]
//                                .getItemLength() + "]")
//                        }
//                    }
//                })
//            }
//            else {
//                // Outputs of other kernels are inputs of the next kernel
//                val nextKernel = kernelList(idx + 1)
//                asScalaBuffer(nextKernel.getArgs).foreach(arg => {
//                    if (arg.getDir == JParameter.DIRECTION.IN) {
//                        write(", " + nextKernel.getKernelName + "_" +
//                          arg.getName)
//                        if (arg.isArray && arg.isPrimitive) {
//                            write("[task * " +
//                              batch_task_num_str + " * " +
//                              arg.asInstanceOf[PrimitiveJParameter]
//                                .getItemLength() + "]")
//                        }
//                    }
//                })
//            }
//
//            // Reference
//            asScalaBuffer(kernel.getRefArgs).foreach(arg => {
//                write(", " + arg.getName)
//            })
//
//            writeln(");")
//        })
//
//        out()
//        writeln("}")
//        out()
//        writeln("}")
        commitToString
    }

    def writeHeaderToString() = {
        sb = new StringBuilder
        kernelList.foreach(k => {
            write(k.getHeader)
        })
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
