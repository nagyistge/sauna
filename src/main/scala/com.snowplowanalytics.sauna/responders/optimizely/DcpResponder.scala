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
package responders
package optimizely

// java
import java.io.{File, InputStream, PrintWriter, StringReader}
import java.util.UUID

// nscala-time
import com.github.nscala_time.time.StaticDateTimeFormat

// scala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source.fromInputStream
import scala.util.control.NonFatal

// akka
import akka.actor.{ActorRef, Props}

// awscala
import awscala.Region
import awscala.s3.{Bucket, S3}

// scala-csv
import com.github.tototoshi.csv._

// sauna
import apis.Optimizely
import loggers.Logger.Notification
import responders.Responder.FileAppeared
import utils._

/**
 * Does stuff for Optimizely Dynamic Customer Profiles feature.
 *
 * @see https://github.com/snowplow/sauna/wiki/Optimizely-responder-user-guide#dcp-batch
 * @param optimizely instance of Optimizely API Wrapper
 * @param optimizelyImportRegion What region uses Optimizely S3 bucket
 * @param logger A logger actor
 */
class DcpResponder(
    optimizely: Optimizely,
    optimizelyImportRegion: String,
    logger: ActorRef)
  extends Responder {

  private val tmpdir = System.getProperty("java.io.tmpdir")

  import DcpResponder._

  val pathPattern =
    """.*com\.optimizely\.dcp/
      |datasource/
      |v1/
      |(.*?)/
      |(.*?)/
      |tsv:([^\/]+)/
      |.+$
    """.stripMargin
       .replaceAll("[\n ]", "")
  val pathRegexp = pathPattern.r


  def publishFile(fileAppeared: FileAppeared, attrs: String, service: String, dataSource: String, credentials: Option[(String, String)]): Unit = {
    credentials match {
      case Some((accessKey, secretKey)) =>
        implicit val s3config = S3(accessKey, secretKey)(Region(optimizelyImportRegion))
        val correctedFile = correct(fileAppeared.is, attrs)
        val fileName = fileAppeared.filePath.substring(fileAppeared.filePath.indexOf(attrs) + attrs.length + 1)
        val s3path = s"dcp/$service/$dataSource/$fileName"

        try {
          Bucket("optimizely-import").put(s3path, correctedFile) // trigger Optimizely to get data from this bucket
          logger ! Notification(s"Successfully uploaded file to S3 bucket 'optimizely-import/$s3path'")
          if (!correctedFile.delete()) println(s"unable to delete file [${correctedFile.getAbsolutePath}]")

        } catch {
          case NonFatal(e) =>
            logger ! Notification(s"Unable to upload to S3 bucket 'optimizely-import/$s3path'. See [$e]")
        }

      case None =>
        logger ! Notification("Unable to get credentials for S3 bucket 'optimizely-import'")
    }
  }


  def process(fileAppeared: FileAppeared): Unit = {
    fileAppeared.filePath match {
      case pathRegexp(service, datasource, attrs) =>
        if (attrs.isEmpty) {
          logger ! Notification("Should be at least one attribute")
        } else if (!attrs.contains("customerId")) {
          logger ! Notification(s"DcpResponder: attribute 'customerId' for [${fileAppeared.filePath}] must be included")
        } else {
          optimizely
            .getOptimizelyS3Credentials(datasource)
            .onSuccess { case credentials => publishFile(fileAppeared, attrs, service, datasource, credentials) }
        }
        // TODO: can we omit non-matching case?
    }
  }

  /**
   * Converts underlying source into Optimizely-friendly format
   *
   * @see http://developers.optimizely.com/rest/customer_profiles/index.html#bulk
   * @see https://github.com/snowplow/sauna/wiki/Optimizely-responder-user-guide#2241-reformatting-for-the-bulk-upload-api
   * @param is An InputStream for some source.
   * @return Corrected file.
   */
  def correct(is: InputStream, header: String): File = {
    val sb = new StringBuilder(header + "\n")
    fromInputStream(is).getLines.map(correctLine).foreach {
      case Some(corrected) => sb.append(corrected + "\n")
      case _ => sb.append("\n")
    }

    val tmpfile = new File(tmpdir, "correct-" + UUID.randomUUID().toString)

    new PrintWriter(tmpfile) { writer =>
      writer.write(sb.toString)
      writer.close()
    }

    tmpfile
  }

  /**
   * Converts the line into Optimizely-friendly format
   *
   * @see http://developers.optimizely.com/rest/customer_profiles/index.html#bulk
   * @see https://github.com/snowplow/sauna/wiki/Optimizely-responder-user-guide#2241-reformatting-for-the-bulk-upload-api
   * @param line A string to be corrected.
   * @return Some(Corrected string) or None, if something (e.g. wrong date format) went wrong.
   */
  def correctLine(line: String): Option[String] = {
    val reader = CSVReader.open(new StringReader(line))(tsvFormat)

    try {
      reader.readNext() // get next line, it should be only one
            .map(_.map(correctWord).mkString(",").replaceAll("\"", ""))   // TODO: really replace all quotes, not trim?

    } catch {
      case _: Exception => None

    } finally {
      reader.close()
    }
  }
}

object DcpResponder {
  val dateFormat = StaticDateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS").withZoneUTC()
  val dateRegexp = "^(\\d{1,4}-\\d{1,2}-\\d{1,2} \\d{1,2}:\\d{1,2}:\\d{1,2}\\.\\d{1,3})$".r

  /**
   * Corrects a single word according to following rules:
   *   1) Change "t" to "true"
   *   2) Change "f" to "false"
   *   3) Change timestamp to epoch
   *
   * @see https://github.com/snowplow/sauna/wiki/Optimizely-responder-user-guide#2241-reformatting-for-the-bulk-upload-api
   * @param word A word to be corrected.
   * @return Corrected word.
   */
  def correctWord(word: String): String = word match {
    case dateRegexp(timestamp) =>
      dateFormat.parseDateTime(timestamp).getMillis.toString
    case "t" => "true"
    case "f" => "false"
    case _ => word
  }

  /**
   * Constructs a Props for DcpResponder actor.
   *
   * @param optimizely Instance of Optimizely.
   * @param optimizelyImportRegion What region uses Optimizely S3 bucket.
   * @param logger Actor with underlying Logger.
   * @return Props for new actor.
   */
  def props(optimizely: Optimizely, optimizelyImportRegion: String, logger: ActorRef): Props =
    Props(new DcpResponder(optimizely, optimizelyImportRegion, logger))
}