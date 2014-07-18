package controllers

import actors.UserActor
import akka.actor.Props
import play.api.Play.current
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.{JsObject, JsValue, Json, __}
import play.api.libs.ws.WS
import play.api.mvc.{Action, Controller, WebSocket}

import scala.concurrent.Future
import scala.util.Random

object Application extends Controller {

  def index = Action {
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

  def ws = WebSocket.acceptWithActor[JsValue, JsValue] { request => out =>
    Props(new UserActor(out))
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
