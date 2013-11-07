import play.Project._

name := """tweetmap"""

version := "1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "org.webjars" %% "webjars-play" % "2.2.0", 
  "org.webjars" % "bootstrap" % "2.3.1",
  "org.webjars" % "leaflet" % "0.6.4"
)

playScalaSettings
