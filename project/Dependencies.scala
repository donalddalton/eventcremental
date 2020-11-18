import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest"               % "3.2.2"  % Test
  lazy val joda      = "joda-time"      % "joda-time"               % "2.10.8"
}
