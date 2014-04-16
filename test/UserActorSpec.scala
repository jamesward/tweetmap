import actors.UserActor
import akka.testkit.TestActorRef
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

      //This is the function called by UserActor when it fetches tweets.  The function fullfills a promise
      //with the tweets, which allows the results to be tested.
      val promiseJson = Promise[Seq[JsValue]]()
      def validateJson(jsValue: JsValue) {
        val tweets = (jsValue \ "statuses").as[Seq[JsValue]]
        promiseJson.success(tweets)
      }

      val userActorRef = TestActorRef(new UserActor(validateJson))

      val querySearchTerm = "scala"
      val jsonQuery = Json.obj("query" -> querySearchTerm)

      // The tests need to be delayed a certain amount of time so the tick message gets fired.  This can be done using either
      // Await.result below or using the Akka test kit within(testDuration) {

      userActorRef ! jsonQuery
      userActorRef.underlyingActor.maybeQuery.getOrElse("") must beEqualTo(querySearchTerm)

      Await.result(promiseJson.future, 10.seconds).length must beGreaterThan(1)

    }
  }
}
