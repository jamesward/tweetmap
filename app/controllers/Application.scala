package controllers

import play.api.mvc.{WebSocket, Action, Controller}
import scala.concurrent.Future
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.libs.json.__
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.{Iteratee, Concurrent}
import play.api.libs.concurrent.Akka
import actors.UserActor
import play.api.Play.current
import scala.util.Random
import akka.actor.Props

object Application extends Controller {

  def index = Action { implicit request =>
    Ok(views.html.index("TweetMap"))
  }
  
  def search(query: String) = Action.async {
    fetchTweets(query).map(tweets => Ok(tweets))
  }

  // searches for tweets based on a query
  def fetchTweets(query: String): Future[JsValue] = {
    val tweetsFuture = WS.url("http://twitter-search-proxy.herokuapp.com/search/tweets").withQueryString("q" -> query).get()
    tweetsFuture.flatMap { response =>
      tweetLatLon((response.json \ "statuses").as[Seq[JsValue]])
    } recover {
      case _ => Seq.empty[JsValue]
    } map { tweets =>
      Json.obj("statuses" -> tweets)
    }
  }

  def ws = WebSocket.using[JsValue] { request =>
    val (out, channel) = Concurrent.broadcast[JsValue]

    val userActor = Akka.system.actorOf(Props(new UserActor(channel.push)))

    val in = Iteratee.foreach[JsValue](userActor ! _).map(_ => Akka.system.stop(userActor))

    (in, out)
  }

  private def putLatLonInTweet(latLon: JsValue) = __.json.update(__.read[JsObject].map(_ + ("coordinates" -> Json.obj("coordinates" -> latLon))))

  private def tweetLatLon(tweets: Seq[JsValue]): Future[Seq[JsValue]] = {
    val tweetsWithLatLonFutures = tweets.map { tweet =>

      if ((tweet \ "coordinates" \ "coordinates").asOpt[Seq[Double]].isDefined) {
        Future.successful(tweet)
      } else {
        val latLonFuture: Future[(Double, Double)] = (tweet \ "user" \ "location").asOpt[String].map(lookupLatLon).getOrElse(Future.successful(randomLatLon))
        latLonFuture.map { latLon =>
          tweet.transform(putLatLonInTweet(Json.arr(latLon._2, latLon._1))).getOrElse(tweet)
        }
      }
    }

    Future.sequence(tweetsWithLatLonFutures)
  }

  private def randomLatLon: (Double, Double) = ((Random.nextDouble * 180) - 90, (Random.nextDouble * 360) - 180)

  private def lookupLatLon(query: String): Future[(Double, Double)] = {
    val locationFuture = WS.url("http://maps.googleapis.com/maps/api/geocode/json").withQueryString(
      "sensor" -> "false",
      "address" -> query
    ).get()

    locationFuture.map { response =>
      (response.json \\ "location").headOption.map { location =>
        ((location \ "lat").as[Double], (location \ "lng").as[Double])
      }.getOrElse(randomLatLon)
    }
  }

}
