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

// typesafe-config
import com.typesafe.config.ConfigFactory

// akka
import akka.actor._
import akka.cluster.Cluster
import akka.cluster.singleton._

// sauna
import actors._
import config._

/**
 * Main class, starts the Sauna program.
 */
object Sauna extends App {

  SaunaOptions.parser.parse(args, SaunaOptions.initial) match {
    case Some(options) => run(options)
    case None => sys.exit(1)
  }

  /**
   * Run sauna with provided configuration
   *
   * @param options options parsed from command line
   */
  def run(options: SaunaOptions): Unit = {
    val respondersConfig = RespondersConfig(options.respondersLocation.getAbsolutePath)
    val observersConfig = ObserversConfig(options.observersLocation.getAbsolutePath)
    val loggersConfig = LoggersConfig(options.observersLocation.getAbsolutePath)

    val config = ConfigFactory
      .parseString(s"akka.remote.netty.tcp.port=${options.port}")
      .withFallback(ConfigFactory.load())

    val system = ActorSystem("sauna", config)
    // TODO: use cluster
    val _ = Cluster(system)

    if (observersConfig.localObserverEnabled) {
      // this actor runs on all nodes
      val _ = system.actorOf(Props(new UbiquitousActor(respondersConfig, observersConfig, loggersConfig)), "UbiquitousActor")
    }

    if (observersConfig.s3ObserverEnabled) {
      // this actor runs on one and only one node
      val _ = system.actorOf(
        ClusterSingletonManager.props(
          singletonProps = Props(new SingletonActor(respondersConfig, observersConfig, loggersConfig)),
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(system)
        ),
        name = "SingletonActor"
      )
    }
  }
}