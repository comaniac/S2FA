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

import java.util.logging.{Handler, Level, Logger}

object Logging {
  val logger = Logger.getLogger("j2fa.log")
  val level = "INFO"
  val logLevel = Level.parse(level)
  logger.setLevel(logLevel)

  def getLevel = level

  def severe(msg: => String) {
    if (logger.isLoggable(Level.SEVERE))
      logger.severe(msg)
  }

  def warn(msg: => String) {
    if (logger.isLoggable(Level.WARNING))
      logger.warning(msg)
  }

  def info(msg: => String) {
    if (logger.isLoggable(Level.INFO))
      logger.info(msg)
  }

  def config(msg: => String) {
    if (logger.isLoggable(Level.CONFIG))
      logger.config(msg)
  }

  def fine(msg: => String) {
    if (logger.isLoggable(Level.FINE))
      logger.fine(msg)
  }

  def finer(msg: => String) {
    if (logger.isLoggable(Level.FINER))
      logger.finer(msg)
  }

  def finest(msg: => String) {
    if (logger.isLoggable(Level.FINEST))
      logger.finest(msg)
  }

}
