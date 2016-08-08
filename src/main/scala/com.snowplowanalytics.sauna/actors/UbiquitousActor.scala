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
package actors

// akka
import akka.actor.Props

// sauna
import observers._

/**
 * This actor supposed to run on all nodes.
 */
class UbiquitousActor(
    parameters: LocalFilesystemConfigParameters,
    saunaOptions: SaunaOptions)
  extends CommonActor(saunaOptions) {

  val localObserver = new LocalObserver(parameters.saunaRoot, responderActors, logger)(self)
  localObserver.start()

  override def postStop(): Unit = {
    localObserver.interrupt()
  }
}

object UbiquitousActor {
  def props(parameters: LocalFilesystemConfigParameters, saunaOptions: SaunaOptions) =
    Props(new UbiquitousActor(parameters, saunaOptions))
}