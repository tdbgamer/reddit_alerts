import Dependencies._

ThisBuild / scalaVersion     := "2.12.8"
ThisBuild / version          := "0.2.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

resolvers += "JCenter" at "https://jcenter.bintray.com"
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies += "org.apache.kafka" % "kafka-clients" % "2.2.0"
libraryDependencies += "org.apache.logging.log4j" % "log4j-core" % "2.11.2"
libraryDependencies += "org.apache.logging.log4j" % "log4j-api" % "2.11.2"
libraryDependencies += "org.apache.logging.log4j" % "log4j-slf4j-impl" % "2.11.2"
libraryDependencies += "net.dean.jraw" % "JRAW" % "1.1.0"
libraryDependencies += "com.fasterxml.jackson.core" % "jackson-annotations" % "2.9.9"
libraryDependencies += "com.fasterxml.jackson.core" % "jackson-core" % "2.9.9"
libraryDependencies += "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.9.9"
libraryDependencies += "org.rogach" %% "scallop" % "3.3.0"
libraryDependencies += "javax.mail" % "javax.mail-api" % "1.6.2"
libraryDependencies += "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"

enablePlugins(JavaAppPackaging)

lazy val root = (project in file("."))
  .settings(
    name := "reddit_alerts",
    libraryDependencies += scalaTest % Test
  )

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
