package actors

import akka.actor.{Actor, ActorRef}
import akka.pattern.pipe
import controllers.Application
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.JsValue

import scala.concurrent.duration._

class UserActor(out: ActorRef) extends Actor {

  var maybeQuery: Option[String] = None

  val tick = context.system.scheduler.schedule(Duration.Zero, 5.seconds, self, FetchTweets)

  def receive = {

    case FetchTweets =>
      maybeQuery.foreach { query =>
        Application.fetchTweets(query).pipeTo(out)
      }

    case message: JsValue =>
      maybeQuery = (message \ "query").asOpt[String]

  }

  override def postStop() {
    tick.cancel()
  }

}

case object FetchTweets