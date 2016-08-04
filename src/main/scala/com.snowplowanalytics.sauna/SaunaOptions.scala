/*
 * Copyright (c) 2012-2016 Snowplow Analytics Ltd. All rights reserved.
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

// java
import java.io.File

/**
 * Options parsed from command line
 * 
 * @param respondersLocation path to directory with responders configurations
 * @param observersLocation path to directory with observers configurations
 * @param loggersLocation path to directory with loggers configurations
 * @param port port number to bind Sauna
 */
case class SaunaOptions(respondersLocation: File, observersLocation: File, loggersLocation: File, port: Int)

object SaunaOptions {
  private[sauna] val initial = SaunaOptions(new File("."), new File("."), new File("."), 1)

  import com.snowplowanalytics.sauna.responders.{ SendgridConfig, SendgridConfigParameters }
  import com.sksamuel.avro4s._
  val out = new File("/vagrant/hello.avro")

  val q = AvroOutputStream[SendgridConfig](out)
  val a = SendgridConfig(true, "com.acme", "test", SendgridConfigParameters(true, "bazbaz"))
  q.write(a)
  q.close()


  val parser = new scopt.OptionParser[SaunaOptions](generated.ProjectSettings.name) {
    head(generated.ProjectSettings.name, generated.ProjectSettings.version)

    opt[File]("responders")
      .required()
      .valueName("<dir>")
      .action((x, c) => c.copy(respondersLocation = x))
      .text("path to directory with responders configurations")

    opt[File]("observers")
      .required()
      .valueName("<dir>")
      .action((x, c) => c.copy(observersLocation = x))
      .text("path to directory with observers configurations")

    opt[File]("loggers")
      .required()
      .valueName("<dir>")
      .action((x, c) => c.copy(loggersLocation = x))
      .text("path to directory with loggers configurations")

    opt[Int]("port")
      .required()
      .valueName("port")
      .action((x, c) => c.copy(port = x))
      .text("port to bind Sauna")
  }
}

