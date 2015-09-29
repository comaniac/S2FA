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

import scala.reflect.ClassTag
import scala.reflect.runtime.universe._
import scala.collection.mutable._

import org.apache.spark._
import org.apache.spark.{Partition, TaskContext}
import org.apache.spark.rdd._
import org.apache.spark.util.random._

class TestSampler[T] extends RandomSampler[T, T] {

  override def setSeed(r: Long) = {}

  override def sample(items: Iterator[T]): Iterator[T] = {
    var out: List[T] = List()

    while (items.hasNext) {
      val v: T = items.next
      if (v.asInstanceOf[Double] > 0.5)
        out = out :+ v
    }
    out.iterator
  }

  override def clone = new TestSampler[T]
}
