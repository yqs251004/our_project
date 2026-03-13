
ThisBuild / organization := "com.riichinexus"
ThisBuild / scalaVersion := "3.3.4"

name := "riichi-nexus"
version := "0.1.0-SNAPSHOT"

val toolkitV = "0.5.0"
val toolkit = "org.scala-lang" %% "toolkit" % toolkitV
val toolkitTest = "org.scala-lang" %% "toolkit-test" % toolkitV
val postgresJdbc = "org.postgresql" % "postgresql" % "42.7.10"

libraryDependencies += toolkit
libraryDependencies += (toolkitTest % Test)
libraryDependencies += postgresJdbc
