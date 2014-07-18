import actors.UserActor
import akka.testkit.{TestProbe, TestActorRef}
import org.specs2.mutable._
import org.specs2.runner._
import org.specs2.time.NoTimeConversions
import org.junit.runner._

import play.api.libs.concurrent.Akka
import play.api.libs.json.{JsValue, Json}
import play.api.test._

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}


@RunWith(classOf[JUnitRunner])
class UserActorSpec extends Specification with NoTimeConversions {

  "UserActor" should {

    "fetch tweets" in new WithApplication {

      //make the Play Application Akka Actor System available as an implicit actor system
      implicit val actorSystem = Akka.system

      val receiverActorRef = TestProbe()

      val userActorRef = TestActorRef(new UserActor(receiverActorRef.ref))

      val querySearchTerm = "scala"
      val jsonQuery = Json.obj("query" -> querySearchTerm)

      // send the query to the Actor
      userActorRef ! jsonQuery

      // test the internal state change
      userActorRef.underlyingActor.maybeQuery.getOrElse("") must beEqualTo(querySearchTerm)

      // the receiver should have received the search results
      val queryResults = receiverActorRef.expectMsgType[JsValue](10.seconds)
      (queryResults \ "statuses").as[Seq[JsValue]].length must beGreaterThan(1)
    }
  }
}
