package controllers

import play.api.mvc.{Action, Controller}
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future
import play.api.Logger
import scala.util.Random
import play.api.libs.json._

object Application extends Controller {
  
  def index = Action {
    Ok(views.html.index.render("Hello Play Framework"))
  }
  
  def tweets(query: String) = Action.async {
    for {
      tweets <- fetchTweets(query)
      tweetsWithLatLon <- tweetLatLon((tweets \ "statuses").as[Seq[JsValue]])
    } yield Ok(Json.obj("statuses" -> tweetsWithLatLon))
  }
  
  // searches for tweets based on a query
  private def fetchTweets(query: String): Future[JsValue] = {
    val tweetsFuture = WS.url("http://twitter-search-proxy.herokuapp.com/search/tweets").withQueryString("q" -> query).get()
    tweetsFuture.map { response =>
      response.json
    } recover {
      case _ => Json.obj("statuses" -> Json.arr())
    }
  }
  
  // transformer that updates the tweet's coordinates with a new value
  private def putLatLonInTweet(latLon: JsValue) = __.json.update(__.read[JsObject].map(_ + ("coordinates" -> Json.obj("coordinates" -> latLon))))
  
  // adds lat lon to tweets that don't have them
  private def tweetLatLon(tweets: Seq[JsValue]): Future[Seq[JsValue]] = {
    
    val tweetsWithLatLonFutures = tweets.map { tweet =>

      if ((tweet \ "coordinates" \ "coordinates").asOpt[Seq[Double]].isDefined) {
        // the tweet already has coordinates
        Future.successful(tweet)
      } else {
        // see if user has a location - if so we need to lookup the lat lon otherwise return a random lat lon
        val latLonFuture: Future[(Double, Double)] = (tweet \ "user" \ "location").asOpt[String].map(lookupLatLon).getOrElse(Future.successful(randomLatLon))
        // create a new tweet that has the lat lon
        latLonFuture.map { latLon =>
          // the twitter API does lon then lat
          tweet.transform(putLatLonInTweet(Json.arr(latLon._2, latLon._1))).getOrElse(tweet)
        }
      }
    }
    
    Future.sequence(tweetsWithLatLonFutures)
  }
  
  // generates a fake lat lon
  private def randomLatLon: (Double, Double) = ((Random.nextDouble * 180) - 90, (Random.nextDouble * 360) - 180)
  
  // looks up a lat lon based on a location name
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