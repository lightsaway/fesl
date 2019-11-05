name := "eso"

version := "0.1"

scalaVersion := "2.13.1"

libraryDependencies += "org.typelevel" %% "cats-core" % "2.0.0"
libraryDependencies += "co.fs2" %% "fs2-core" % "2.0.1"
libraryDependencies += "org.typelevel" %% "cats-testkit-scalatest" % "1.0.0-RC1" % Test
libraryDependencies += "com.dimafeng" %% "testcontainers-scala" % "0.33.0" % Test
libraryDependencies += "org.testcontainers" % "postgresql" % "1.12.3" % Test
libraryDependencies += "org.tpolecat" %% "skunk-core" % "0.0.4"


val circeVersion = "0.12.3"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)