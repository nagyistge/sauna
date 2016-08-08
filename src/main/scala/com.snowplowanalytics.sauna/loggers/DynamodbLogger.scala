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

// scala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

// awscala
import awscala._
import awscala.dynamodbv2.DynamoDB

// sauna
import Logger._

abstract class DynamodbLogger(parameters: AmazonDynamodbConfigParameters) extends Logger {
  // AWS credentials
  implicit val region = Region(parameters.awsRegion)
  val credentials = new Credentials(parameters.awsAccessKeyId, parameters.awsSecretAccessKey)

  // DynamoDB
  val ddb = DynamoDB(credentials)
  val ddbTable = ddb.table(parameters.dynamodbTableName)
                    .getOrElse(throw new RuntimeException(s"No table [${parameters.dynamodbTableName}] was found"))

  /**
   * Writes the message to DynamoDb table.
   */
  def log(message: Manifestation): Unit = {
    import message._

    val _ = Future { // make non-blocking call
      ddbTable.put(uid, name, "status" -> status, "description" -> description, "lastModified" -> lastModified)(ddb)
    }
  }
}