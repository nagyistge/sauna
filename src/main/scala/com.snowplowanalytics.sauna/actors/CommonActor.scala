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
import akka.actor._
import com.snowplowanalytics.sauna.loggers.Logger.Manifestation

// sauna
import apis._
import config._
import loggers._
import responders.optimizely._
import responders.sendgrid._

/**
 * Implementations of this actor run on cluster nodes. They launch different observers during lifetime.
 * This class collects common stuff (apis, configs, loggers).
 */
abstract class CommonActor(respondersConfig: RespondersConfig,
                           observersConfig: ObserversConfig,
                           loggersConfig: LoggersConfig) extends Actor {
  // logger
  val logger = (loggersConfig.hipchatEnabled, loggersConfig.dynamodbEnabled) match {
    case (true, true) =>
      context.actorOf(Props(new HipchatLogger(loggersConfig) {
        val dynamodbLogger = context.actorOf(Props(new DynamodbLogger(observersConfig, loggersConfig) with StdoutLogger))
        override def log(message: Manifestation): Unit = dynamodbLogger ! message
      }))

    case (true, false) =>
      context.actorOf(Props(new HipchatLogger(loggersConfig) with StdoutLogger))

    case (false, true) =>
      context.actorOf(Props(new DynamodbLogger(observersConfig, loggersConfig) with StdoutLogger))

    case _ =>
      context.actorOf(Props(new StdoutLogger {}))
  }

  // apis
  // note that even if api was disabled in config, no error will happen, because
  // default sentinel value "" is applied as token, and first real use happens inside responders,
  // and it wont happen if appropriate responder was not activated
  val optimizely = new Optimizely(respondersConfig.optimizelyToken, logger)
  val sendgrid = new Sendgrid(respondersConfig.sendgridToken, logger)

  // responders
  var responderActors = List.empty[ActorRef]
  if (respondersConfig.targetingListEnabled) {
    responderActors +:= context.actorOf(TargetingList(optimizely, logger), "TargetingList")
  }
  if (respondersConfig.dynamicClientProfilesEnabled) {
    responderActors +:= context.actorOf(DCPDatasource(optimizely, observersConfig.saunaRoot, respondersConfig.optimizelyImportRegion, logger), "DCPDatasource")
  }
  if (respondersConfig.recipientsEnabled) {
    responderActors +:= context.actorOf(Recipients(logger, sendgrid), "Recipients")
  }

  def receive: Receive = {
    case _ =>
  }
}