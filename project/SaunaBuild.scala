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
import Keys._
import sbtassembly.AssemblyKeys._
import sbtassembly.{PathList, MergeStrategy}

object SaunaBuild {

  lazy val buildSettings = Seq[Setting[_]](
    organization  := "com.snowplowanalytics",
    name          := "sauna",
    version       := "0.1.0-M1",
    description   := "A decisioning and response framework",
    scalaVersion  := "2.11.8",
    scalacOptions := Seq(
                       "-deprecation",
                       "-encoding", "UTF-8",
                       "-feature",
                       "-unchecked",
                       "-Xfatal-warnings",
                       "-Ywarn-dead-code",
                       "-Ywarn-inaccessible",
                       "-Ywarn-infer-any",
                       "-Ywarn-nullary-override",
                       "-Ywarn-nullary-unit",
                       "-Ywarn-numeric-widen",
                       "-Ywarn-unused",
                       "-Ywarn-unused-import",
                       "-Ywarn-value-discard"
                     ),
    javacOptions := Seq(
                      "-source", "1.8",
                      "-target", "1.8",
                      "-Xlint"
                    ),

    scalacOptions in (Compile, console) ~= (_ filterNot (_ == "-Xfatal-warnings")),
    scalacOptions in (Test, console) := (scalacOptions in (Compile, console)).value,

    // force scala version
    // http://stackoverflow.com/questions/27809280/suppress-sbt-eviction-warnings
    ivyScala := ivyScala.value map { _.copy(overrideScalaVersion = true) },

    parallelExecution in Test := false, // possible race bugs

    test in assembly := {}, // speed up packaging
    assemblyMergeStrategy in assembly := {
      case PathList("org", "apache", "commons", "logging", xs @ _*) =>
        // by default, sbt-assembly checks class files with same relative paths for 100% identity
        // however, sometimes different dependency-of-dependency might have different library version, e.g. content
        // so, we should chose one of conflicting versions
        MergeStrategy.first

      case x =>
        val oldStrategy = (assemblyMergeStrategy in assembly).value
        oldStrategy(x)
    }
  )
}