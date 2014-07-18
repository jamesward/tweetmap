name := "tweetmap"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.1"

libraryDependencies ++= Seq(
  ws,
  "org.webjars" % "bootstrap" % "2.3.1",
  "org.webjars" % "angularjs" % "1.2.16",
  "org.webjars" % "angular-leaflet-directive" % "0.7.6",
  "com.typesafe.akka" %% "akka-testkit" % "2.3.3" % "test"
)

//includeFilter in (Assets, LessKeys.less) := "main.less"