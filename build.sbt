scalaVersion := "2.12.4"

name := "smetrics"
organization := "net.scalactite"
version := "0.0.1"

scalacOptions := Seq(
  "-Xfatal-warnings",
  "-Xlint:_",
  "-Ywarn-unused",
  "-Ywarn-value-discard",
  "-deprecation",
  "-feature",
  "-unchecked"
)

libraryDependencies ++= Seq(
  "io.dropwizard.metrics" % "metrics-core" % "4.0.0",
  "org.scala-lang" % "scala-reflect" % scalaVersion.value,
  // test stuff
  "org.mockito" % "mockito-core" % "2.13.0",
  "org.scalatest" %% "scalatest" % "3.0.4"
)

addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

licenses += ("MIT" -> url("http://opensource.org/licenses/MIT"))

bintrayRepository := "scalactite"

