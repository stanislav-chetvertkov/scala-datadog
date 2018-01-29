name := "scala-datadog"

version := "0.1"

scalaVersion := "2.12.4"

resolvers += Resolver.bintrayRepo("hseeberger", "maven")

libraryDependencies ++= Seq(
  "io.circe" %% "circe-derivation" % "0.9.0-M1",
  "io.circe" %% "circe-generic-extras" % "0.9.0-M2",
  "de.heikoseeberger" %% "akka-http-circe" % "1.19.0"
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-http" % "10.0.11",
  "com.typesafe.akka" %% "akka-http-testkit" % "10.0.11" % Test,
  "org.scalatest" %% "scalatest" % "3.0.4" % Test
)
