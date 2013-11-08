package controllers

import play.api.mvc.{WebSocket, Action, Controller}
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future
import play.api.Logger
import scala.util.Random
import play.api.libs.json._
import play.api.libs.iteratee.{Iteratee, Concurrent}
import play.api.libs.concurrent.Akka
import akka.actor.Props
import actors.UserActor
import play.api.Play.current

object Application extends Controller {
  
  def index = Action { implicit request =>
    Ok(views.html.index("Hello Play Framework"))
  }
  
  def tweets(query: String) = Action.async {
    fetchTweets(query).map(tweets => Ok(tweets))
  }
  
  // searches for tweets based on a query
  def fetchTweets(query: String): Future[JsObject] = {
    val tweetsFuture = WS.url("http://twitter-search-proxy.herokuapp.com/search/tweets").withQueryString("q" -> query).get()
    tweetsFuture.flatMap { response =>
      tweetLatLon((response.json \ "statuses").as[Seq[JsValue]])
    } recover {
      case _ => Seq.empty[JsValue]
    } map { tweets =>
      Json.obj("statuses" -> tweets)
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
  
  def ws = WebSocket.using[JsValue] { request =>
    val (out, channel) = Concurrent.broadcast[JsValue]

    def pushUpdate(jsValue:JsValue ) = {
      channel.push(jsValue)
    }

    val userActor = Akka.system.actorOf(UserActor.props(pushUpdate))
    
    val in = Iteratee.foreach[JsValue](userActor ! _).map(_ => Akka.system.stop(userActor))

    (in, out)
  }
  
}