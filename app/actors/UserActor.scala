package actors

import akka.actor.Actor
import play.api.libs.json.JsValue
import play.api.libs.iteratee.Concurrent.Channel
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import controllers.Application

class UserActor(channel: Channel[JsValue]) extends Actor {
  
  var maybeQuery: Option[String] = None
  
  val tick = context.system.scheduler.schedule(Duration.Zero, 30.seconds, self, FetchTweets)
  
  def receive = {
    
    case FetchTweets =>
      maybeQuery.map { query =>
        Application.fetchTweets(query).map(channel.push(_))
      }
      
    case message: JsValue =>
      maybeQuery = (message \ "query").asOpt[String]

  }

  override def postStop() {
    tick.cancel()
  }
  
}

case object FetchTweets
