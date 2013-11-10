import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import play.api.libs.json.JsValue
import play.api.test._
import play.api.test.Helpers._

/**
 * Add your spec here.
 * You can mock out a whole application including requests, plugins etc.
 * For more information, consult the wiki.
 */
@RunWith(classOf[JUnitRunner])
class ApplicationSpec extends Specification {

  "Application" should {

    "send 404 on a bad request" in new WithApplication{
      route(FakeRequest(GET, "/boum")) must beNone
    }

    "render index template" in new WithApplication {
      val html = views.html.index("Coco")

      contentAsString(html) must contain("Hello Coco")
    }

    "render the index page" in new WithApplication{
      val home = route(FakeRequest(GET, "/")).get

      status(home) must equalTo(OK)
      contentType(home) must beSome.which(_ == "text/html")
      contentAsString(home) must contain ("Tweets")
    }
    
    "search for tweets" in new WithApplication {
      val search = controllers.Tweets.search("typesafe")(FakeRequest())

      status(search) must equalTo(OK)
      contentType(search) must beSome("application/json")
      (contentAsJson(search) \ "statuses").as[Seq[JsValue]].length must beGreaterThan(0)
    }
    
  }
}
