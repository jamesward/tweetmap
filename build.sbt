import play.Project._

name := "tweetmap"

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.2.1-2",
  "org.webjars" % "bootstrap" % "2.3.1",
  "org.webjars" % "angularjs" % "1.2.16",
  "org.webjars" % "angular-leaflet-directive" % "0.7.6",
  "com.typesafe.akka" %% "akka-testkit" % "2.2.0" % "test"
)

playScalaSettings