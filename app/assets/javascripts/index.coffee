$ ->
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

  map = L.map('map').setView([0, 0], 2)
  L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}.png', {}).addTo(map)

  displayTweets = (tweets) ->
    $.each tweets.statuses, (index, tweet) ->
      L.marker([tweet.coordinates.coordinates[1], tweet.coordinates.coordinates[0]])
       .addTo(map)
       .bindPopup(tweet.text)
       .openPopup()