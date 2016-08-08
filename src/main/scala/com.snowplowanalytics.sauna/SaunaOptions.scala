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

// java
import java.io.File

import com.snowplowanalytics.sauna.observers.{AmazonS3Config, LocalFilesystemConfig}


// avro4s
import com.sksamuel.avro4s._

// sauna
import responders._
import loggers.{ AmazonDynamodbConfig, HipchatConfig }


/**
 * Options parsed from command line
 * 
 * @param respondersLocation path to directory with responders configurations
 * @param observersLocation path to directory with observers configurations
 * @param loggersLocation path to directory with loggers configurations
 * @param port port number to bind Sauna
 */
case class SaunaOptions(respondersLocation: File, observersLocation: File, loggersLocation: File, port: Int) {

  val optimizely: Option[OptimizelyConfig] =
    responder[OptimizelyConfig]

  val sendgrid: Option[SendgridConfig] =
    responder[SendgridConfig]

  val local: Option[LocalFilesystemConfig] =
    observer[LocalFilesystemConfig]

  val s3: Option[AmazonS3Config] =
    observer[AmazonS3Config]

  val amazonDynamodb: Option[AmazonDynamodbConfig] =
    logger[AmazonDynamodbConfig]

  val hipchat: Option[HipchatConfig] =
    logger[HipchatConfig]

  def logger[S: SchemaFor: FromRecord]: Option[S] =
    getConfig(loggersLocation)

  def responder[S: SchemaFor: FromRecord]: Option[S] =
    getConfig(respondersLocation)

  def observer[S: SchemaFor: FromRecord]: Option[S] =
    getConfig(observersLocation)

  private[sauna] def getConfig[S: SchemaFor: FromRecord](location: File): Option[S] =
    location.listFiles.toList.flatMap(x => AvroInputStream.json[S](x).iterator.toList).headOption
}

object SaunaOptions {
  private[sauna] val initial = SaunaOptions(new File("."), new File("."), new File("."), 1)

  val parser = new scopt.OptionParser[SaunaOptions](generated.ProjectSettings.name) {
    head(generated.ProjectSettings.name, generated.ProjectSettings.version)

    opt[File]("responders")
      .required()
      .valueName("<dir>")
      .action((x, c) => c.copy(respondersLocation = x))
      .text("path to directory with responders configurations")
      .validate(f => if (f.isDirectory && f.canRead) success else failure("responders must be a directory with read access"))

    opt[File]("observers")
      .required()
      .valueName("<dir>")
      .action((x, c) => c.copy(observersLocation = x))
      .text("path to directory with observers configurations")
      .validate(f => if (f.isDirectory && f.canRead) success else failure("observers must be a directory with read access"))

    opt[File]("loggers")
      .required()
      .valueName("<dir>")
      .action((x, c) => c.copy(loggersLocation = x))
      .text("path to directory with loggers configurations")
      .validate(f => if (f.isDirectory && f.canRead) success else failure("loggers must be a directory with read access"))

    opt[Int]("port")
      .required()
      .valueName("port")
      .action((x, c) => c.copy(port = x))
      .text("port to bind Sauna")
      .validate(p => if (p > 1) success else failure("port need to be positive number"))
  }
}

