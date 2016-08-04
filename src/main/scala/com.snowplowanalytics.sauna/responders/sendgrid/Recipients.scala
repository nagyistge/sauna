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
package sendgrid

// java
import java.io.{InputStream, StringReader}

// nscala-time
import com.github.nscala_time.time.StaticDateTimeFormat

// scala
import scala.concurrent.ExecutionContext.Implicits.global
import scala.io.Source.fromInputStream
import scala.util.Try
import scala.util.control.NonFatal

// akka
import akka.actor.{ActorRef, Props}

// play
import play.api.libs.json._

// scala-csv
import com.github.tototoshi.csv._

// sauna
import apis.Sendgrid
import loggers.Logger.Notification
import responders.Responder.FileAppeared
import utils._

/**
 * Does stuff for Sendgrid import recipients feature
 *
 * @see https://sendgrid.com/docs/User_Guide/Marketing_Campaigns/contacts.html
 * @see https://github.com/snowplow/sauna/wiki/SendGrid-responder-user-guide
 * @param sendgrid Sendgrid API Wrapper
 * @param logger A logger actor.
 */
class Recipients(sendgrid: Sendgrid, logger: ActorRef) extends Responder {
  import Recipients._

  /**
   * Regular expression allowing to extract TSV attributes from file path
   */
  val pathPattern =
    """.*com\.sendgrid\.contactdb/
      |recipients/
      |v1/
      |tsv:([^\/]+)/
      |.+$
    """.stripMargin.replaceAll("[\n ]", "")
  val pathRegexp = pathPattern.r

  // TODO: handle wrong path?
  def process(fileAppeared: FileAppeared): Unit = {
    fileAppeared.filePath match {
      case pathRegexp(attrs) if attrs.isEmpty =>
        logger ! Notification("Should be at least one attribute")
      case pathRegexp(attrs) if !attrs.contains("email") =>
          logger ! Notification("Attribute 'email' must be included")
      case pathRegexp(attrs) =>
        val keys = attrs.split(",")
        getData(fileAppeared.is).foreach { chunk =>
          processData(keys, chunk)
        }
    }
  }

  /**
   * This method does the second part of job for "import recipients" feature.
   * It handles errors and sends result to Sendgrid.
   *
   * @param keys Seq of attribute keys, repeated for each recipient from `valuess`.
   * @param valuess Seq of recipients, where recipient is a seq of attribute values.
   *                Each `values` in `valuess` should have one length with `keys`.
   */
  def processData(keys: Seq[String], valuess: Seq[Seq[String]]): Unit = {
    val (probablyValid, definitelyInvalid) = valuess.partition(_.length == keys.length)

    // deal with 100% corrupted data
    definitelyInvalid.foreach { invalidValues =>
      logger ! Notification(s"Unable to process [${invalidValues.mkString("\t")}], " +
                            s"it has only ${invalidValues.length} columns, when ${keys.length} are required.")
    }

    val json = makeValidJson(keys, probablyValid)
    sendgrid
      .postRecipients(json)
      .foreach { response => handleErrors(probablyValid.length, response.body) }

    Thread.sleep(WAIT_TIME) // note that for actor all messages come from single queue
                            // so new `fileAppeared` will be processed after current one
  }

  /**
   * Used to handle possible errors from Sendgrid's response.
   * Informs user via logger ! Notification.
   * Heavily relies to Sendgrid's response json structure.
   *
   * @param totalRecordsNumber Sometimes records "disappear".
   *                           So, originally were 10, error_count = 2, updated_count = 4, new_count = 3.
   *                           One record was lost. This param is expected total records number.
   * @param jsonText A json text from Sendgrid.
   *                 example:

                     {
                        "error_count":1,
                        "error_indices":[
                           2
                        ],
                        "errors":[
                           {
                              "error_indices":[
                                 2
                              ],
                              "message":"date type conversion error"
                           }
                        ],
                        "new_count":2,
                        "persisted_recipients":[
                           "Ym9iQGZvby5jb20=",
                           "a2FybEBiYXIuZGU="
                        ],
                        "updated_count":0
                     }
   */
  def handleErrors(totalRecordsNumber: Int, jsonText: String): Unit =
    try {
      val json = Json.parse(jsonText)
      val errorCount = (json \ "error_count").as[Int]
      val errorIndices = (json \ "error_indices").as[Seq[Int]]
      val errorsOpt = (json \ "errors").asOpt[Seq[JsObject]]
      val newCount = (json \ "new_count").as[Int]
      val updatedCount = (json \ "updated_count").as[Int]

      // trying to get error explanation
      for {
        errorIndex <- errorIndices
        errors     <- errorsOpt
      } {
        errors.map(_.value).find(_.apply("error_indices").as[Seq[Int]].contains(errorIndex)) match {
          case Some(error) =>
            val reason = error.apply("message").as[String]
            logger ! Notification(s"Error $errorIndex caused due to [$reason].")

          case None =>
            logger ! Notification(s"Unable to find reason for error $errorIndex.")
        }
      }

      if (errorCount + newCount + updatedCount != totalRecordsNumber) {
        val message = "For some reasons, several records disappeared. It's rare Sendgrid bug. Double-check you input."
        logger ! Notification(message)
      }

    } catch {
      case NonFatal(e) =>
        logger ! Notification(s"Got exception ${e.getMessage} while parsing Sendgrid's response.")
    }
}

object Recipients {
  val LINE_LIMIT = 1000   // https://sendgrid.com/docs/API_Reference/Web_API_v3/Marketing_Campaigns/contactdb.html#Add-a-Single-Recipient-to-a-List-POST
  val WAIT_TIME = 667L    // https://sendgrid.com/docs/API_Reference/Web_API_v3/Marketing_Campaigns/contactdb.html#Add-Recipients-POST

  val dateFormatFull = StaticDateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss.SSS").withZoneUTC()
  val dateRegexpFull = "^(\\d{1,4}-\\d{1,2}-\\d{1,2} \\d{1,2}:\\d{1,2}:\\d{1,2}\\.\\d{1,3})$".r
  val dateFormatShort = StaticDateTimeFormat.forPattern("yyyy-MM-dd").withZoneUTC()
  val dateRegexpShort = "^(\\d{1,4}-\\d{1,2}-\\d{1,2})$".r

  /**
   * Constructs a Props for Recipients actor.
   *
   * @param sendgrid Instance of Sendgrid.
   * @param logger Actor with underlying Logger.
   * @return Props for new actor.
   */
  def apply(logger: ActorRef, sendgrid: Sendgrid): Props =
    Props(new Recipients(sendgrid, logger))

  /**
   * This method does the first part of job for "import recipients" feature.
   * It gets file content, parses it and splits into smaller chunks to satisfy Sendgrid's limitations.
   *
   * Deepest-level container contains tab-separated values
   * Second-level container contains lines
   * First-level container contains chunks of lines according to [[LINE_LIMIT]]
   *
   * @param is InputStream to data file.
   * @return Iterator of valuess data. Valuess are extracted from the file.
   * @see `Recipients.makeValidJson`
   */
  def getData(is: InputStream): Iterator[Seq[Seq[String]]] =
    fromInputStream(is).getLines().flatMap(valuesFromTsv).grouped(LINE_LIMIT)

  /**
   * Creates a Sendgrid-friendly JSON from given keys and values
   * Also transforms everything look-alike datetime (ISO-8601) into Unix epoch
   * Length of `keys` **MUST** be equal to amount of length of inner Seq
   *
   * For example, for `keys` = Seq("name1", "name2"),
   *                  `values` = Seq(Seq("value11", "value12"), Seq("value21", "value22")),
   * result would be:
   *
   * [
   *   {
   *     "name1": "value11",
   *     "name2": "value12"
   *   },
   *   {
   *     "name1": "value21",
   *     "name2": "value22"
   *   }
   * ]
   *
   * @param keys Seq of attribute keys, repeated for each recipient from `valuess`
   * @param values Seq of recipients, where recipient is a seq of attribute values.
   *                Each `values` in `valuess` must already have one length with `keys`
   * @return Sendgrid-friendly JSON
   * @see https://github.com/snowplow/sauna/wiki/SendGrid-responder-user-guide#214-response-algorithm
   */
  private[sendgrid] def makeValidJson(keys: Seq[String], values: Seq[Seq[String]]): JsArray = {
    val recipients = for {
      vals            <- values
      _                = assert(vals.length == keys.length)
      correctedValues  = vals.map(correctTimestamps)}
      yield {
        val zipped = keys.zip(correctedValues.map(x => Json.toJson(x)))
        val recipientData = JsObject(zipped.toMap)
        postProcess(recipientData)
      }

    JsArray(recipients)
  }

  /**
   * Transform JSON derived from TSV using following rules:
   * + empty string becomes null
   * + numeric string becomes number
   *
   * @param json one-level JSON object extracted from TSV
   * @return cleaned JSON
   */
  def postProcess(json: JsObject): JsObject = {
    JsObject(json.value.mapValues {
      case JsString("") => JsNull
      case JsString(v)  =>
        Try(v.toDouble).toOption.map(d => JsNumber(d)).getOrElse(JsString(v))
      case other => other
    })
  }

  /**
   * Tries to extract values from given tab-separated line.
   *
   * @param tsvLine A tab-separated line.
   * @return Some[Seq of values]. In case of exception, returns None.
   */
  def valuesFromTsv(tsvLine: String): Option[Seq[String]] = {
    val reader = CSVReader.open(new StringReader(tsvLine))(tsvFormat)

    try {
      reader.readNext() // get next line, it should be only one
    } catch {
      case NonFatal(_) => None
    } finally {
      reader.close()
    }
  }

  /**
   * Transform string, possible containing ISO-8601 datetime to string with
   * Unix-epoch (in seconds) or do nothing if string doesn't conform format
   *
   * @param s string to be corrected.
   * @return Corrected word.
   * @see https://github.com/snowplow/sauna/wiki/SendGrid-responder-user-guide#214-response-algorithm
   */
  def correctTimestamps(s: String): String = s match {
    case dateRegexpFull(timestamp) =>
      (dateFormatFull.parseDateTime(timestamp).getMillis / 1000).toString
    case dateRegexpShort(timestamp) =>
      (dateFormatShort.parseDateTime(timestamp).getMillis / 1000).toString
    case _ => s
  }
}