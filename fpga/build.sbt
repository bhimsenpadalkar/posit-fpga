organization := "com.thoughtworks"

version := "1.0.0"

name := "posit-fpga"

scalaVersion := "2.11.12"

crossScalaVersions := Seq("2.11.12", "2.12.4")

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked", "-language:reflectiveCalls")

// Provide a managed dependency on X if -DXVersion="" is supplied on the command line.
// The following are the current "release" versions.
val defaultVersions = Map(
  "chisel3" -> "3.1.+",
  "chisel-iotesters" -> "1.2.+"
)

libraryDependencies ++= (Seq("chisel3","chisel-iotesters").map {
  dep: String => "edu.berkeley.cs" %% dep % sys.props.getOrElse(dep + "Version", defaultVersions(dep)) })


// Recommendations from http://www.scalatest.org/user_guide/using_scalatest_with_sbt
logBuffered in Test := false

// Disable parallel execution when running tests.
//  Running tests in parallel on Jenkins currently fails.
parallelExecution in Test := false

