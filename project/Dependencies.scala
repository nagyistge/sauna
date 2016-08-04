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

import sbt._

object Dependencies {
  object V {
    val typesafeConfig = "1.3.0"
    val totoshi = "1.2.2"
    val seratch = "0.5.+"
    val nscalaTime = "2.12.0"
    val scopt = "3.5.0"
    val avro4s = "1.4.3"
    val play = "2.4.8"
    val akka = "2.4.8"
    val scalaTest = "2.2.4"
  }

  object Libraries {
    // Java
    val typesafeConfig   = "com.typesafe"           % "config"              % V.typesafeConfig

    // Scala
    val totoshi          = "com.github.tototoshi"   %% "scala-csv"          % V.totoshi
    val seratch          = "com.github.seratch"     %% "awscala"            % V.seratch
    val nscalaTime       = "com.github.nscala-time" %% "nscala-time"        % V.nscalaTime
    val scopt            = "com.github.scopt"       %% "scopt"              % V.scopt
    val avro4s           = "com.sksamuel.avro4s"    %% "avro4s-core"        % V.avro4s
    val playJson         = "com.typesafe.play"      %% "play-json"          % V.play
    val playWs           = "com.typesafe.play"      %% "play-ws"            % V.play
    val akkaActor        = "com.typesafe.akka"      %% "akka-actor"         % V.akka
    val akkaCluster      = "com.typesafe.akka"      %% "akka-cluster"       % V.akka
    val akkaClusterTools = "com.typesafe.akka"      %% "akka-cluster-tools" % V.akka

    // Test
    val akkaTestkit      = "com.typesafe.akka"      %% "akka-testkit"       % V.akka              // scope to test?
    val scalaTest        = "org.scalatest"          %% "scalatest"          % V.scalaTest         % "test"
  }

}
