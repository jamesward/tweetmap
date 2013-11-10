## TweetMap Workshop

### Setup

1. Install Activator: Copy the zip file to your computer, extract the zip, double-click on the `activator` or `activator.bat` file to launch the Activator UI
2. Create a new app with the `Hello Play Framework` template
3. Optional: Open the project in an IDE: Select `Code` then `Open` then select your IDE and follow the instructions to generate the project files and open the project in Eclipse or IntelliJ


### Basics

Slides: http://presos.jamesward.com/introduction_to_the_play_framework-scala

* Running the App
* Running the Tests
* Routes
* Controllers
* Views


### Setup Part 2

1. Delete:

    * `app/controllers/MessageController.scala`
    * `app/controllers/MainController.java`
    * `app/assets/javascripts/index.js`
    * `test/IntegrationTest.java`
    * `test/MainControllerTest.java`
    * `test/MessageControllerSpec.scala`

2. Remove the following lines from `conf/routes`:

        GET        /                                 controllers.MainController.index()
        GET        /message                          controllers.MessageController.getMessage()
        GET        /assets/javascripts/routes        controllers.MessageController.javascriptRoutes()

3. Remove the following line from `app/views/main.scala.html`:

        <script type="text/javascript" src="@routes.MessageController.javascriptRoutes"></script> 


### Reactive Requests

1. Create a new route in `conf/routes`:

        GET        /                     controllers.Tweets.index
        GET        /tweets               controllers.Tweets.search(query: String)

2. Create a new Controller `app/controllers/Tweets.scala`:

        package controllers
        
        import play.api.mvc.{Action, Controller}
        import scala.concurrent.Future
        import play.api.libs.json.{JsValue, Json}
        import play.api.libs.ws.WS
        import play.api.libs.concurrent.Execution.Implicits.defaultContext
        
        object Tweets extends Controller {
        
          def index = Action { implicit request =>
            Ok(views.html.index("Tweets"))
          }
        
          def search(query: String) = Action.async {
            fetchTweets(query).map(tweets => Ok(tweets))
          }
          
          def fetchTweets(query: String): Future[JsValue] = {
            val tweetsFuture = WS.url("http://twitter-search-proxy.herokuapp.com/search/tweets").withQueryString("q" -> query).get()
            tweetsFuture.map { response =>
              response.json
            } recover {
              case _ => Json.obj("responses" -> Json.arr())
            }
          }
        
        }

3. Test it: http://localhost:9000/tweets?query=typesafe


### Fake Tweet Service (In-case of bad Internet)

1. Create a new route in `conf/routes`:

        GET        /fakeTweets               controllers.Tweets.fakeTweets

2. Create a new method in `app/controllers/Tweets.scala`:

        def fakeTweets = Action {
          val json = Json.arr("statuses" -> Seq(Json.obj("text" -> "test tweet 1"), Json.obj("text" -> "test tweet 2")))
          Ok(Json.toJson(json))
        }


### Test the Controller

1. Create a new `test/TweetsSpec.scala` file:

        import org.specs2.mutable._
        import org.specs2.runner._
        import org.junit.runner._
        
        import play.api.libs.json.JsValue
        import play.api.test._
        import play.api.test.Helpers._
        
        @RunWith(classOf[JUnitRunner])
        class TweetsSpec extends Specification {
        
          "Application" should {
        
            "render index template" in new WithApplication {
              val html = views.html.index("Coco")
        
              contentAsString(html) must contain("Coco")
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

2. Run the tests


### CoffeeScript Asset Compiler

1. Update the `app/views/main.scala.html` file replacing the contents of `<div class="container-fluid">` with:

        <form id="queryForm" class="navbar-search pull-left">
            <input id="twitterQuery" name="twitterQuery" type="text" class="search-query" placeholder="Search">
        </form>

2. Update the `app/views/index.scala.html` file replacing the `div` and `button` with:

        <ul id="tweets"></ul>

3. Create a new file `app/assets/javascripts/index.coffee` containing:

        $ ->
          $("#queryForm").submit (event) ->
            event.preventDefault()
            query = $("#twitterQuery").val()
            $.get "/tweets?query=" + query, (data) ->
              displayTweets(data)
        
          displayTweets = (tweets) ->
            $("#tweets").empty()
            $.each tweets.statuses, (index, tweet) ->
              $("#tweets").append $("<li>").text(tweet.text)

4. Run the app, make a query, and verify the tweets show up: http://localhost:9000


### Test the Form (Requires Firefox)

1. Create a new `test/IntegrationSpec.scala` file:

        import java.util.concurrent.TimeUnit
        import org.specs2.mutable._
        import org.specs2.runner._
        import org.junit.runner._
        
        import play.api.test._
        import play.api.test.Helpers._
        
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

2. Run the tests


### WebSocket

1. Create a new route:

        GET        /ws                   controllers.Tweets.ws

2. Add a new controller method in `app/controllers/Tweets.scala`:

        def ws = WebSocket.using[JsValue] { request =>
          val (out, channel) = Concurrent.broadcast[JsValue]
      
          def pushUpdate(jsValue: JsValue) = {
            channel.push(jsValue)
          }
    
          val userActor = Akka.system.actorOf(UserActor.props(pushUpdate))
        
          val in = Iteratee.foreach[JsValue](userActor ! _).map(_ => Akka.system.stop(userActor))
    
          (in, out)
        }
        
3. Create an Actor in `app/actors/UserActor.scala` containing:

        package actors
        
        import akka.actor.{Props, Actor}
        import play.api.libs.json.JsValue
        import scala.concurrent.duration._
        import play.api.libs.concurrent.Execution.Implicits.defaultContext
        import controllers.Tweets
        
        class UserActor(tweetUpdate: JsValue => Unit) extends Actor {
        
          var maybeQuery: Option[String] = None
        
          val tick = context.system.scheduler.schedule(Duration.Zero, 30.seconds, self, FetchTweets)
        
          def receive = {
        
            case FetchTweets =>
              maybeQuery.map { query =>
                Tweets.fetchTweets(query).map(tweetUpdate)
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

4. Update the `app/views/index.scala.html` file:

        @(message: String)(implicit request: RequestHeader)

5. Update the `app/views/main.scala.html` file:

        @(title: String)(content: Html)(implicit request: RequestHeader)

        <body data-ws="@routes.Tweets.ws.webSocketURL()">

6. Update the `app/assets/javascripts/index.coffee` file:

        ws = new WebSocket $("body").data("ws")
          ws.onmessage = (event) ->
            data = JSON.parse event.data
            displayTweets(data)
            
        $("#queryForm").submit (event) ->
          event.preventDefault()
          query = $("#twitterQuery").val()
          ws.send JSON.stringify
            query: query
          $.get "/tweets?query=" + query, (data) ->
            displayTweets(data)


### Test the Actor

1. Create a new file in `test/UserActorSpec.scala` containing:

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
        
              implicit val actorSystem = Akka.system
        
              val promiseJson = Promise[Seq[JsValue]]()
              def validateJson(jsValue: JsValue) {
                val tweets = (jsValue \ "statuses").as[Seq[JsValue]]
                promiseJson.success(tweets)
              }
        
              val userActorRef = TestActorRef(new UserActor(validateJson, 1.second))
        
              val querySearchTerm = "scala"
              val jsonQuery = Json.obj("query" -> querySearchTerm)
        
              userActorRef ! jsonQuery
              userActorRef.underlyingActor.maybeQuery.getOrElse("") must beEqualTo(querySearchTerm)
        
              Await.result(promiseJson.future, 10.seconds).length must beGreaterThan(1)
        
            }
          }
        }


### Add the Tweet Map

1. Add a new dependency to the `build.sbt` file:

        "org.webjars" % "leaflet" % "0.6.4"

2. Restart the Play app

3. Include the Leaflet CSS and JS in the `app/views/main.scala.html` file:

        <link rel='stylesheet' href='@routes.WebJarAssets.at(WebJarAssets.locate("leaflet.css"))'>
        <script type='text/javascript' src='@routes.WebJarAssets.at(WebJarAssets.locate("leaflet.js"))'></script>

4. Replace the `<ul>` in `app/views/index.scala.html` with:

        <div id="map"></div>

5. Update the `app/assets/stylesheets/index.less` file with:

        #map {
          position: absolute;
          top: 40px;
          bottom: 0px;
          left: 0px;
          right: 0px;
        }

5. Update the `app/assets/javascripts/index.coffee` file with:

        map = L.map('map').setView([0, 0], 2)
        L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}.png', {}).addTo(map)
        
        displayTweetsOnMap = (map, tweets) ->
          $.each tweets.statuses, (index, tweet) ->
            L.marker([tweet.coordinates.coordinates[1], tweet.coordinates.coordinates[0]])
             .addTo(map)
             .bindPopup(tweet.text)
             .openPopup()

6. Create new functions in `app/controllers/Tweets.scala` to get (or fake) the location of the tweets:

        private def putLatLonInTweet(latLon: JsValue) = __.json.update(__.read[JsObject].map(_ + ("coordinates" -> Json.obj("coordinates" -> latLon))))
               
        private def tweetLatLon(tweets: Seq[JsValue]): Future[Seq[JsValue]] = {
          val tweetsWithLatLonFutures = tweets.map { tweet =>
             
            if ((tweet \ "coordinates" \ "coordinates").asOpt[Seq[Double]].isDefined) {
              Future.successful(tweet)
            } else {
              val latLonFuture: Future[(Double, Double)] = (tweet \ "user" \ "location").asOpt[String].map(lookupLatLon).getOrElse(Future.successful(randomLatLon))
              latLonFuture.map { latLon =>
                tweet.transform(putLatLonInTweet(Json.arr(latLon._2, latLon._1))).getOrElse(tweet)
              }
            }
          }
                 
          Future.sequence(tweetsWithLatLonFutures)
        }
               
        private def randomLatLon: (Double, Double) = ((Random.nextDouble * 180) - 90, (Random.nextDouble * 360) - 180)
               
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

7. In `app/controllers/Tweets.scala` update the `fetchTweets` function to use the new `tweetLatLon` function:

        def fetchTweets(query: String): Future[JsValue] = {
          val tweetsFuture = WS.url("http://twitter-search-proxy.herokuapp.com/search/tweets").withQueryString("q" -> query).get()
          tweetsFuture.flatMap { response =>
            tweetLatLon((response.json \ "statuses").as[Seq[JsValue]])
          } recover {
            case _ => Seq.empty[JsValue]
          } map { tweets =>
            Json.obj("statuses" -> tweets)
          }
        }
