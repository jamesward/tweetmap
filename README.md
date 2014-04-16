## TweetMap Workshop

### Setup

1. Install Activator: Copy the zip file to your computer, extract the zip, double-click on the `activator` or `activator.bat` file to launch the Activator UI

2. Create a new app with the `Hello Play Framework (Scala Only)` template

3. Optional: Open the project in an IDE: Select `Code` then `Open` then select your IDE and follow the instructions to generate the project files and open the project in Eclipse or IntelliJ


### Reactive Requests

1. Create a new route in `conf/routes`:

        GET        /tweets               controllers.Application.search(query: String)

2. Create a new reactive request handler in `app/controllers/Application.scala`:

        import play.api.mvc.{WebSocket, Action, Controller}
        import scala.concurrent.Future
        import play.api.libs.json.{JsObject, JsValue, Json}
        import play.api.libs.json.__
        import play.api.libs.ws.WS
        import play.api.libs.concurrent.Execution.Implicits.defaultContext
        import play.api.libs.iteratee.{Iteratee, Concurrent}
        import play.api.libs.concurrent.Akka
        import actors.UserActor
        import play.api.Play.current
        import scala.util.Random
        import akka.actor.Props


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

3. Test it: http://localhost:9000/tweets?query=typesafe


### Test the Controller

1. Update the `test/ApplicationSpec.scala` file with these tests:

        import org.specs2.mutable._
        import org.specs2.runner._
        import org.junit.runner._
        
        import play.api.libs.json.JsValue
        import play.api.test._
        import play.api.test.Helpers._


          "Application" should {
        
            "render index template" in new WithApplication {
              val html = views.html.index("Coco")
        
              contentAsString(html) must contain("Coco")
            }
        
            "render the index page" in new WithApplication{
              val home = route(FakeRequest(GET, "/")).get
        
              status(home) must equalTo(OK)
              contentType(home) must beSome.which(_ == "text/html")
              contentAsString(home) must contain ("TweetMap")
            }
            
            "search for tweets" in new WithApplication {
              val search = controllers.Tweets.search("typesafe")(FakeRequest())
        
              status(search) must equalTo(OK)
              contentType(search) must beSome("application/json")
              (contentAsJson(search) \ "statuses").as[Seq[JsValue]].length must beGreaterThan(0)
            }
            
          }

2. Run the tests


### AngularJS UI

1. Add WebJar dependency to `build.sbt`:

        "org.webjars" % "angularjs" % "1.2.16"

2. Enable AngularJS in the `app/views/main.scala.html` file:

        <html ng-app="myApp">
        
        
        <script src="@routes.WebJarAssets.at(WebJarAssets.locate("angular.min.js"))"></script>
        <script type='text/javascript' src='@routes.Assets.at("javascripts/index.js")'></script>

3. Update the `app/views/main.scala.html` file replacing the contents of `<div class="container-fluid">` with:


            <div class="container-fluid" ng-controller="Search">
                <a href="#" class="brand pull-left">@title</a>
                <form class="navbar-search pull-left" ng-submit="search()">
                    <input ng-model="query" class="search-query" placeholder="Search">
                </form>
            </div>

4. Replace the `app/views/index.scala.html` file:

        @(message: String)(implicit request: RequestHeader)
        
        @main(message) {
        
            <div ng-controller="Tweets">
                <ul>
                    <li ng-repeat="tweet in tweets">{{tweet.text}}</li>
                </ul>
            </div>
        
        }

5. Create a new file `app/assets/javascripts/index.js` containing:

        var app = angular.module('myApp', []);
        
        app.factory('Twitter', function($http, $timeout) {
            
            var twitterService = {
                tweets: [],
                query: function (query) {
                    $http({method: 'GET', url: '/tweets', params: {query: query}}).
                        success(function (data) {
                            twitterService.tweets = data.statuses;
                        });
                }
            };
            
            return twitterService;
        });
        
        app.controller('Search', function($scope, $http, $timeout, Twitter) {
        
            $scope.search = function() {
                Twitter.query($scope.query);
            };
        
        });
        
        app.controller('Tweets', function($scope, $http, $timeout, Twitter) {
        
            $scope.tweets = [];
            
            $scope.$watch(
                function() {
                    return Twitter.tweets;
                },
                function(tweets) { 
                    $scope.tweets = tweets;
                }
            );
            
        });

6. Run the app, make a query, and verify the tweets show up: http://localhost:9000


### WebSocket

1. Create a new route in `conf/routes`:

        GET        /ws                   controllers.Application.ws

2. Add a new controller method in `app/controllers/Application.scala`:

          def ws = WebSocket.using[JsValue] { request =>
            val (out, channel) = Concurrent.broadcast[JsValue]
        
            val userActor = Akka.system.actorOf(Props(new UserActor(channel.push)))
        
            val in = Iteratee.foreach[JsValue](userActor ! _).map(_ => Akka.system.stop(userActor))
        
            (in, out)
          }
        
3. Create an Actor in `app/actors/UserActor.scala` containing:

        package actors
        
        import akka.actor.Actor
        import play.api.libs.json.JsValue
        import scala.concurrent.duration._
        import play.api.libs.concurrent.Execution.Implicits.defaultContext
        import controllers.Application
        
        class UserActor(tweetUpdate: JsValue => Unit) extends Actor {
        
          var maybeQuery: Option[String] = None
        
          val tick = context.system.scheduler.schedule(Duration.Zero, 5.seconds, self, FetchTweets)
        
          def receive = {
        
            case FetchTweets =>
              maybeQuery.map { query =>
                Application.fetchTweets(query).map(tweetUpdate)
              }
        
            case message: JsValue =>
              maybeQuery = (message \ "query").asOpt[String]
        
          }
        
          override def postStop() {
            tick.cancel()
          }
        
        }
        
        case object FetchTweets

4. Update the `app.factory` section of `app/assets/javascripts/index.js` with:

        var ws = new WebSocket("ws://localhost:9000/ws");
        
        var twitterService = {
            tweets: [],
            query: function (query) {
                $http({method: 'GET', url: '/tweets', params: {query: query}}).
                    success(function (data) {
                        twitterService.tweets = data.statuses;
                    });
                ws.send(JSON.stringify({query: query}));
            }
        };
        
        ws.onmessage = function(event) {
            $timeout(function() {
                twitterService.tweets = JSON.parse(event.data).statuses;
            });
        };


### Test the Actor

1. Add `akka-testkit` to the dependencies in `build.sbt`:

        "com.typesafe.akka" %% "akka-testkit" % "2.2.0" % "test"

2. Regenerate the IDE project files to include the new dependency

3. Create a new file in `test/UserActorSpec.scala` containing:

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

4. Run the tests


### Add the Tweet Map

1. Add a new dependency to the `build.sbt` file:

        "org.webjars" % "angular-leaflet-directive" % "0.7.6",

2. Restart the Play app

3. Include the Leaflet CSS and JS in the `app/views/main.scala.html` file:

        <link rel='stylesheet' href='@routes.WebJarAssets.at(WebJarAssets.locate("leaflet.css"))'>
        <script type='text/javascript' src='@routes.WebJarAssets.at(WebJarAssets.locate("leaflet.js"))'></script>
        <script type='text/javascript' src='@routes.WebJarAssets.at(WebJarAssets.locate("angular-leaflet-directive.min.js"))'></script>

4. Replace the `<ul>` in `app/views/index.scala.html` with:

        <leaflet width="100%" height="500px" markers="markers"></leaflet>

5. Update the `app.controller('Tweets'` section of `app/assets/javascripts/index.js` file with the following:

            $scope.tweets = [];
            $scope.markers = [];
            
            $scope.$watch(
                function() {
                    return Twitter.tweets;
                },
                function(tweets) { 
                    $scope.tweets = tweets;
                    
                    $scope.markers = tweets.map(function(tweet) {
                        return {
                            lng: tweet.coordinates.coordinates[0],
                            lat: tweet.coordinates.coordinates[1],
                            message: tweet.text,
                            focus: true
                        }
                    });
                }
            );
       
7. Create new functions in `app/controllers/Application.scala` to get (or fake) the location of the tweets:
        
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

8. In `app/controllers/Application.scala` update the `fetchTweets` function to use the new `tweetLatLon` function:

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

9. Refresh your browser to see the TweetMap!