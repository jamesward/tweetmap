## TweetMap

### Setup

1. Install Activator: Copy the zip file to your computer, extract the zip, double-click on the `activator` or `activator.bat` file to launch the Activator UI
2. Create a new app with the `Hello Play Framework` template
3. Optional: Open the project in an IDE: Select `Code` then `Open in...` then select your IDE and follow the instructions to generate the project files and open the project in Eclipse or IntelliJ

### Basics

* Running the App
* Running the Tests
* Routes
* Controllers
* Views

### Reactive Requests

1. Create a new route in `conf/routes`:

        GET        /tweets               controllers.Application.tweets(query: String)

2. Create two new methods in `app/controllers/Application.scala`:

        def tweets(query: String) = Action.async {
          fetchTweets(query).map(tweets => Ok(tweets))
        }
      
        // searches for tweets based on a query
        def fetchTweets(query: String): Future[JsObject] = {
          val tweetsFuture = WS.url("http://twitter-search-proxy.herokuapp.com/search/tweets").withQueryString("q" -> query).get()
          tweetsFuture.flatMap { response =>
            response.json
          } recover {
            case _ => Json.obj("responses" -> Json.arr())
          }
        }

3. Test it: http://localhost:9000/tweets?query=typesafe

### Fake Tweet Service (In-case of bad Internet)

1. Create a new route in `conf/routes`:

        GET        /fakeTweets               controllers.Application.fakeTweets

2. Create a new method in `app/controllers/Application.scala`:

        def fakeTweets = Action {
          val json = Json.arr("statuses" -> Seq(Json.obj("text" -> "test tweet 1"), Json.obj("text" -> "test tweet 2")))
          Ok(Json.toJson(json))
        }

### Test the Controller


### CoffeeScript Asset Compiler

1. Update the `app/views/main.scala.html` file replacing the contents of `<div class="container-fluid">` with:

        <form id="queryForm" class="navbar-search pull-left">
            <input id="twitterQuery" type="text" class="search-query" placeholder="Search">
        </form>

2. Update the `app/views/index.scala.html` file:

        <script src="@routes.Assets.at("javascripts/index.min.js")"></script>
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

### WebSocket

1. Create a new route:

        GET        /ws                   controllers.Application.ws

2. Add a new controller method:

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

4. Update the `app/views/index.scala.html` file:

        @(message: String)(implicit request: RequestHeader)


        <body data-ws="@routes.Application.ws.webSocketURL()">

5. Update the `app/controllers/Application.scala`

        def index = Action { implicit request =>
          Ok(views.html.index("Hello Play Framework"))
        }

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

### Add the Tweet Map

1. Add a new dependency to the `build.sbt` file:

        "org.webjars" % "leaflet" % "0.6.4"

2. Include the Leaflet CSS and JS in the `app/views/main.scala.html` file:

        <link rel='stylesheet' href='@routes.WebJarAssets.at(WebJarAssets.locate("leaflet.css"))'>
        <script type='text/javascript' src='@routes.WebJarAssets.at(WebJarAssets.locate("leaflet.js"))'></script>

3. Replace the `<ul>` in `app/views/index.scala.html` with:

        <div id="map"></div>

4. Update the `app/assets/javascripts/index.coffee` file with:

        map = L.map('map').setView([0, 0], 2)
        L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}.png', {}).addTo(map)
        
        displayTweetsOnMap = (map, tweets) ->
          $.each tweets, (index, tweet) ->
            L.marker([tweet.coordinates.coordinates[1], tweet.coordinates.coordinates[0]])
             .addTo(map)
             .bindPopup(tweet.text)
             .openPopup()

5. Create new functions in `app/controllers/Application.scala` to get (or fake) the location of the tweets:

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

6. Update the `fetchTweets` function to use the new `tweetLatLon` function:

        tweetLatLon((response.json \ "statuses").as[Seq[JsValue]])

