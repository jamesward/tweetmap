import actors.UserActor
import akka.testkit.TestActorRef
import java.util.concurrent.TimeUnit
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import play.api.libs.concurrent.Akka
import play.api.libs.json.{JsValue, Json}
import play.api.test._

import scala.concurrent.duration._
import scala.concurrent.{Await, Promise}


@RunWith(classOf[JUnitRunner])
class UserActorSpec extends Specification {

  "UserActor" should {

    "fetch tweets" in new WithApplication {

      //make the Play Application Akka Actor System available as an implicit actor system
      implicit val actorSystem = Akka.system

      //not sure if it makes sense to validate json?
      val promiseJson = Promise[Seq[JsValue]]()

      def validateJson(jsValue: JsValue) {
        val tweets = (jsValue \ "statuses").as[Seq[JsValue]]
        promiseJson.success(tweets)
      }

      // why doesn't this work?
      //does it have to do with specs2 time conversions -i.e. see:
      //  import org.specs2.time.NoTimeConversions
      // val tickDuration = 1 second
      val tickDuration = Duration(1, TimeUnit.SECONDS)
      val userActorRef = TestActorRef(new UserActor(validateJson, tickDuration))

      val querySearchTerm = "scala"
      val jsonQuery = Json.obj("query" -> querySearchTerm)

      val testDuration = Duration(10, TimeUnit.SECONDS)

      // The tests need to be delayed a certain amount of time so the tick message gets fired.  This can be done using either
      // Await.result below or using the Akka test kit within(testDuration) {

      userActorRef ! jsonQuery
      userActorRef.underlyingActor.maybeQuery.getOrElse("") must beEqualTo(querySearchTerm)

      val tweets: Seq[JsValue] = Await.result(promiseJson.future, testDuration)
      println(s"tweets  = ${tweets.length}")
      tweets.length must beGreaterThan(1)

      // because the UserActor sends the message to itself is there anyway to insert a probe to test it is sent?
      // expectMsg(FetchTweets)

    }
  }
}
