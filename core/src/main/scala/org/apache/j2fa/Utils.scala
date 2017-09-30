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
import java.lang.reflect.Method
import com.amd.aparapi.internal.util.{Utils => AparapiUtils}

object Utils {

  def getMethodSignature(m: Method): String = {
    val params = m.getParameterTypes.map(e =>
        AparapiUtils.convertToBytecodeType(e.getName, true))
    val ret = AparapiUtils.convertToBytecodeType(m.getReturnType.getName, true)
    var sig = "(";
    params.foreach(e => sig += e)
    sig + ")" + ret
  }

  def getLegalKernelName(str : String): String = {
    str.replace("(", "_").replace(")", "").replace("$", "")
  }
}
