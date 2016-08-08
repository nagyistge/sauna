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
import com.snowplowanalytics.sauna.observers.{AmazonS3Config, LocalFilesystemConfig}
import com.typesafe.config.ConfigFactory

// akka
import akka.actor._
import akka.cluster.Cluster
import akka.cluster.singleton._

// sauna
import actors._

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

     val config = ConfigFactory
       .parseString(s"akka.remote.netty.tcp.port=${options.port}")
       .withFallback(ConfigFactory.load())

     val system = ActorSystem("sauna", config)
     // TODO: use cluster
     val _ = Cluster(system)

    options.local match {
      case Some(LocalFilesystemConfig(true, _, _, params)) =>
        // this actor runs on all nodes
        val _ = system.actorOf(UbiquitousActor.props(params, options), "UbiquitousActor")
      case _ => ()
    }

    options.s3 match {
      case Some(AmazonS3Config(true, _, _, params)) =>
        // this actor runs on one and only one node
        val _ = system.actorOf(
          ClusterSingletonManager.props(
            singletonProps = SingletonActor.props(params, options),
            terminationMessage = PoisonPill,
            settings = ClusterSingletonManagerSettings(system)
          ),
          name = "SingletonActor"
        )
      case _ => ()
    }
  }
}