import Dependencies._

ThisBuild / scalaVersion     := "2.13.3"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.eventcremental"
ThisBuild / organizationName := "eventcremental"

lazy val root = (project in file("."))
  .settings(
    name := "eventcremental",
    libraryDependencies += scalaTest % Test
  )

