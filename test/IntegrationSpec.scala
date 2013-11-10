import java.util.concurrent.TimeUnit
import org.specs2.mutable._
import org.specs2.runner._
import org.junit.runner._

import play.api.test._
import play.api.test.Helpers._

/**
 * add your integration spec here.
 * An integration test will fire up a whole play application in a real (or headless) browser
 */
@RunWith(classOf[JUnitRunner])
class IntegrationSpec extends Specification {

  "Application" should {

    "work from within a browser" in new WithBrowser(webDriver = FIREFOX) {

      browser.goTo("http://localhost:" + port)

      browser.submit("#queryForm", "twitterQuery" -> "typesafe")
      
      browser.waitUntil(10, TimeUnit.SECONDS) {
        browser.find(".leaflet-marker-icon").size() > 0
      }
    }
    
  }
}
