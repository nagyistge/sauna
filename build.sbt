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

lazy val root = (project in file("."))
  .settings(
    name          := "sauna",
    version       := "0.1.0-M1",
    description   := "A decisioning and response framework"
  )
  .settings(BuildSettings.buildSettings)
  .settings(sbtavrohugger.SbtAvrohugger.avroSettings)
  .settings(BuildSettings.scalifySettings)
  .settings(
    libraryDependencies ++= Seq(
      Dependencies.Libraries.typesafeConfig,
      Dependencies.Libraries.totoshi,
      Dependencies.Libraries.seratch,
      Dependencies.Libraries.nscalaTime,
      Dependencies.Libraries.scopt,
      Dependencies.Libraries.avro4s,
      Dependencies.Libraries.playJson,
      Dependencies.Libraries.playWs,
      Dependencies.Libraries.akkaActor,
      Dependencies.Libraries.akkaCluster,
      Dependencies.Libraries.akkaClusterTools,
      Dependencies.Libraries.akkaTestkit,
      Dependencies.Libraries.scalaTest
    )
  )
