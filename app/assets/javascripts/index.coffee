$ ->
  map = L.map('map').setView([0, 0], 2)
  L.tileLayer('http://{s}.tile.osm.org/{z}/{x}/{y}.png', {}).addTo(map)
  
  $("#queryForm").submit (event) ->
    event.preventDefault()
    $.get "/tweets?query=" + $("#twitterQuery").val(), (data) ->
      $("#tweets").empty()
      $.each data.statuses, (index, item) ->
        displayTweetOnMap(map, item)
        #$("#tweets").append $("<li>").text(item.text)

        
displayTweetOnMap = (map, tweet) ->
  L.marker([tweet.coordinates.coordinates[1], tweet.coordinates.coordinates[0]])
   .addTo(map)
   .bindPopup(tweet.text)
   .openPopup()