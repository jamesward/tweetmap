## TweetMap Workshop

### Setup

1. Install Activator: Copy the zip file to your computer, extract the zip, double-click on the `activator` or `activator.bat` file to launch the Activator UI

2. Create a new app with the `Play Scala Seed` template

3. Optional: Open the project in an IDE: Select `Code` then `Open` then select your IDE and follow the instructions to generate the project files and open the project in Eclipse or IntelliJ


### Reactive Requests

1. Create a new route in `conf/routes`:

        GET        /tweets               controllers.Application.search(query: String)

2. Create a new reactive request handler in `app/controllers/Application.scala`:

        import play.api.Play.current
        import play.api.libs.concurrent.Execution.Implicits.defaultContext
        import play.api.libs.json._
        import play.api.libs.ws.WS
        import play.api.mvc._
        
        import scala.concurrent.Future

          def index = Action {
            Ok(views.html.index("TweetMap"))
          }
        
          def search(query: String) = Action.async {
            fetchTweets(query).map(tweets => Ok(tweets))
          }
        
          def fetchTweets(query: String): Future[JsValue] = {
            val tweetsFuture = WS.url("http://search-twitter-proxy.herokuapp.com/search/tweets").withQueryString("q" -> query).get()
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
        

            "send 404 on a bad request" in new WithApplication{
              route(FakeRequest(GET, "/boum")) must beNone
            }
        
            "render index template" in {
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
              val search = controllers.Application.search("typesafe")(FakeRequest())
        
              status(search) must equalTo(OK)
              contentType(search) must beSome("application/json")
              (contentAsJson(search) \ "statuses").as[Seq[JsValue]].length must beGreaterThan(0)
            }
            
          }

2. Run the tests


### Bootstrap UI

1. Add WebJar dependency to `build.sbt`:

        "org.webjars" % "bootstrap" % "2.3.1"

2. Restart Play

3. Delete `public/stylesheets`

4. Create a `app/assets/stylesheets/main.less` file:

        body {
          padding-top: 50px;
        }

5. Update the `app/views/main.scala.html` file:

        <link rel='stylesheet' href='@routes.Assets.at("lib/bootstrap/css/bootstrap.min.css")'>
        
        
        <body>
            <div class="navbar navbar-fixed-top">
                <div class="navbar-inner">
                    <div class="container-fluid">
                        <a href="#" class="brand pull-left">@title</a>
                    </div>
                </div>
            </div>
            <div class="container">
                @content
            </div>
        </body>

6. Update the `app/views/index.scala.html` file:

        @(message: String)
        
        @main(message) {
        
            hello, world
        
        }

7. Run the app and make sure it looks nice: http://localhost:9000


### AngularJS UI

1. Add WebJar dependency to `build.sbt`:

        "org.webjars" % "angularjs" % "1.2.16"

2. Enable AngularJS in the `app/views/main.scala.html` file:

        <html ng-app="myApp">
        
        
        <script src="@routes.Assets.at("lib/angularjs/angular.min.js")"></script>
        <script type='text/javascript' src='@routes.Assets.at("javascripts/main.js")'></script>

3. Update the `app/views/main.scala.html` file replacing the contents of `<body>` with:

        <div class="container-fluid" ng-controller="Search">
            <a href="#" class="brand pull-left">@title</a>
            <form class="navbar-search pull-left" ng-submit="search()">
                <input ng-model="query" class="search-query" placeholder="Search">
            </form>
        </div>

4. Replace the `app/views/index.scala.html` file:

        @(message: String)
        
        @main(message) {
        
            <div ng-controller="Tweets">
                <ul>
                    <li ng-repeat="tweet in tweets">{{tweet.text}}</li>
                </ul>
            </div>
        
        }

5. Create a new file `app/assets/javascripts/main.js` containing:

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



6. Restart the Play app

7. Run the app, make a query, and verify the tweets show up: http://localhost:9000


### WebSocket

1. Create a new route in `conf/routes`:

        GET        /ws                   controllers.Application.ws

2. Add a new controller method in `app/controllers/Application.scala`:

          import actors.UserActor
          import akka.actor.Props
          import play.api.libs.json.JsValue
          import play.api.mvc.WebSocket

          def ws = WebSocket.acceptWithActor[JsValue, JsValue] { request => out =>
            Props(new UserActor(out))
          }
        
3. Create an Actor in `app/actors/UserActor.scala` containing:

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

4. Update the `app.factory` section of `app/assets/javascripts/main.js` with:

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
        
        return twitterService;


### Test the Actor

1. Add `akka-testkit` to the dependencies in `build.sbt`:

        "com.typesafe.akka" %% "akka-testkit" % "2.3.3" % "test"

2. Regenerate the IDE project files to include the new dependency

3. Create a new file in `test/UserActorSpec.scala` containing:

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

4. Run the tests


### Add the Tweet Map

1. Add a new dependency to the `build.sbt` file:

        "org.webjars" % "angular-leaflet-directive" % "0.7.6"

2. Restart the Play app

3. Include the Leaflet CSS and JS in the `app/views/main.scala.html` file:

        <link rel='stylesheet' href='@routes.Assets.at("lib/leaflet/leaflet.css")'>
        <script type='text/javascript' src='@routes.Assets.at("lib/leaflet/leaflet.js")'></script>
        <script type='text/javascript' src='@routes.Assets.at("lib/angular-leaflet-directive/angular-leaflet-directive.min.js")'></script>

4. Replace the `<ul>` in `app/views/index.scala.html` with:

        <leaflet width="100%" height="500px" markers="markers"></leaflet>

5. Update the first line of the `app/assets/javascripts/main.js` file with the following:

            var app = angular.module('myApp', ["leaflet-directive"]);

6. Update the `app.controller('Tweets'` section of the `app/assets/javascripts/main.js` file with the following:

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
                        };
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
            val tweetsFuture = WS.url("http://search-twitter-proxy.herokuapp.com/search/tweets").withQueryString("q" -> query).get()
            tweetsFuture.flatMap { response =>
              tweetLatLon((response.json \ "statuses").as[Seq[JsValue]])
            } recover {
              case _ => Seq.empty[JsValue]
            } map { tweets =>
              Json.obj("statuses" -> tweets)
            }
          }

9. Refresh your browser to see the TweetMap!