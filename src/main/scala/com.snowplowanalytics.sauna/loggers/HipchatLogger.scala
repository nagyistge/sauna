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
package loggers

// sauna
import loggers.Logger._
import utils._

abstract class HipchatLogger(parameters: HipchatConfigParameters) extends Logger {
  import HipchatLogger._

  /**
   * Makes notification to some HipChat room.
   */
  def log(message: Notification): Unit = {
    import message._

    val content = s"""{"color":"green","message":"$text","notify":false,"message_format":"text"}"""

    val _ = wsClient.url(urlPrefix + s"room/${parameters.roomId}/notification")
      .withHeaders("Authorization" -> s"Bearer ${parameters.token}", "Content-Type" -> "application/json")
      .post(content)
  }
}

object HipchatLogger {
  val urlPrefix = "https://api.hipchat.com/v2/"
}