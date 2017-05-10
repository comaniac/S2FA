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

import com.amd.aparapi.Config

class TopKernelWriter(filePath: String, kernelList : List[Kernel]) {
    val logger = Logger.getLogger(Config.getLoggerName)

    if (logger.isLoggable(Level.FINEST)) {
        logger.finest("Subkernels: ")
        kernelList.foreach(k => logger.finest("-> " + k.getKernelName))
    }

    // Open the top kernel file
    val pw = new PrintWriter(new File(filePath + "/kernel_top.cpp"))

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
    write("void kernel_top(")

    // Take input arguments from the first kernel
    var isFirst = true
    val firstKernelArgList = kernelList.head.getArgList
    firstKernelArgList.foreach(arg => {
        if (!arg._1.startsWith("s2fa_out")) {
            if (!isFirst)
                write(",\n\t")
            write(arg._2 + " " + arg._1)
            isFirst = false
        }
    })

    // Take the output arguments from the last kernel
    val lastKernelArgList = kernelList.last.getArgList
    lastKernelArgList.foreach(arg => {
        if (arg._1.startsWith("s2fa_out"))
            write(",\n\t" + arg._2 + " " + arg._1)
    })
    writeln(") {")

    kernelList.foreach(kernel => {
        write(kernel.getKernelName + "(")
        val argList = kernel.getArgList
        writeln(");")
    })

    writeln("}")
    pw.close

    // Writer utils

    def writeln(): Unit = writeln("")

    def writeln(_string: String): Unit = {
        write(_string + "\n")
    }

    def write(_string: String): Unit = {
        pw.write(_string)
    }
}