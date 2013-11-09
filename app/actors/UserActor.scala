package actors

import akka.actor.{Props, Actor}
import play.api.libs.json.JsValue
import scala.concurrent.duration._
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import controllers.Application

class UserActor(tweetUpdate: JsValue => Unit) extends Actor {

  var maybeQuery: Option[String] = None

  val tick = context.system.scheduler.schedule(Duration.Zero, 30.seconds, self, FetchTweets)

  def receive = {

    case FetchTweets =>
      maybeQuery.map { query =>
        Application.fetchTweets(query).map(tweetUpdate(_))
      }

    case message: JsValue =>
      maybeQuery = (message \ "query").asOpt[String]

  }

  override def postStop() {
    tick.cancel()
  }

}

case object FetchTweets

object UserActor {

  def props(tweetUpdate: JsValue => Unit): Props =
    Props(new UserActor(tweetUpdate))

}