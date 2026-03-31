
ThisBuild / organization := "com.riichinexus"
ThisBuild / scalaVersion := "3.3.4"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .settings(
    name := "riichi-nexus",
    libraryDependencies ++= Seq(
      "org.scala-lang" %% "toolkit" % "0.5.0",
      "org.typelevel" %% "cats-effect" % "3.5.4",
      "org.http4s" %% "http4s-dsl" % "0.23.27",
      "org.http4s" %% "http4s-ember-server" % "0.23.27",
      "org.http4s" %% "http4s-circe" % "0.23.27",
      "io.circe" %% "circe-generic" % "0.14.9",
      "io.circe" %% "circe-parser" % "0.14.9",
      "org.typelevel" %% "log4cats-slf4j" % "2.7.0",
      "org.slf4j" % "slf4j-simple" % "2.0.13",
      "org.postgresql" % "postgresql" % "42.7.10",
      "org.scala-lang" %% "toolkit-test" % "0.5.0" % Test
    ),
    Compile / run / mainClass := Some("Main")
  )
