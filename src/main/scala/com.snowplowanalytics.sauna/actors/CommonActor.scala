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

// sauna
import apis._
import loggers._
import loggers.Logger.Manifestation
import responders.optimizely._
import responders.sendgrid._
import responders._

/**
 * Implementations of this actor run on cluster nodes. They launch different observers during lifetime.
 * This class collects common stuff (apis, configs, loggers).
 */
abstract class CommonActor(saunaOptions: SaunaOptions) extends Actor {

  val logger = (saunaOptions.hipchat, saunaOptions.amazonDynamodb) match {
    case (Some(HipchatConfig(true, _, _, hipcharParams)), Some(AmazonDynamodbConfig(true, _, _, dynamodbParams))) =>

      val dynamodbLogger = context.actorOf(Props(new DynamodbLogger(dynamodbParams) with StdoutLogger))

      context.actorOf(Props(new HipchatLogger(hipcharParams) {
        override def log(message: Manifestation): Unit = dynamodbLogger ! message
      }))

    case (Some(HipchatConfig(true, _, _, params)), _) =>
      context.actorOf(Props(new HipchatLogger(params) with StdoutLogger))

    case (_, Some(AmazonDynamodbConfig(true, _, _, params))) =>
      context.actorOf(Props(new DynamodbLogger(params) with StdoutLogger))

    case _ =>
      context.actorOf(Props(new StdoutLogger {}))
  }

  // responders
  var responderActors = List.empty[ActorRef]
  
  saunaOptions.optimizely match {
    case Some(OptimizelyConfig(true, _, _, params)) =>
      val optimizelyApiWrapper = new Optimizely(params.token, logger)
      
      if (params.targetingListEnabled)
        responderActors +:= context.actorOf(TargetingListResponder.props(optimizelyApiWrapper, logger), "TargetingListResponder")

      if (params.dynamicClientProfilesEnabled)
        responderActors +:= context.actorOf(DcpResponder.props(optimizelyApiWrapper, params.awsRegion, logger), "DcpResponder")

    case _ => ()
  }

  saunaOptions.sendgrid match {
    case Some(SendgridConfig(true, _, _, params)) =>
      val sendgridApiWrapper = new Sendgrid(params.token, logger)

      if (params.recipientsEnabled)
        responderActors +:= context.actorOf(RecipientsResponder.props(logger, sendgridApiWrapper), "RecipientsResponder")

    case _ => ()
  }

  def receive: Receive = {
    case _ =>
  }
}