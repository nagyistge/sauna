/*
 * Copyright (c) 2016 Snowplow Analytics Ltd. All rights reserved.
 *
 * This program is licensed to you under the Apache License Version 2.0,
 * and you may not use this file except in compliance with the Apache License Version 2.0.
 * You may obtain a copy of the Apache License Version 2.0 at http://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the Apache License Version 2.0 is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Apache License Version 2.0 for the specific language governing permissions and limitations there under.
 */
package com.snowplowanalytics.sauna
package observers

// scala
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

// akka
import akka.util.Timeout

/**
 * Observer should keep an eye on some place where new files may appear.
 *
 * This trait collects common useful stuff for possible implementations.
 */
trait Observer extends Thread {

  setDaemon(true)

   // timeout for Future responses
  implicit val timeout = Timeout(10.seconds)

   // for ask pattern
  implicit val context: ExecutionContext = ExecutionContext.Implicits.global
}