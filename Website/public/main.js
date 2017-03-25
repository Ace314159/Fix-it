firebase.initializeApp({
        apiKey: " AIzaSyC-le5lTljaHlZ6dwv4dOCKGyc-DnO8Ing",
        authDomain: "fix-it-1.firebaseapp.com",
        databaseURL: "https://fix-it-1.firebaseio.com",
        storageBucket: "fix-it-1.appspot.com"
    });
firebase.auth().onAuthStateChanged(function(user) {
    if(!user) {
        window.location.replace("login.html");
    }
});

/**********************************MARKERS AND LIST************************************/
var yellow_marker = "https://maps.google.com/mapfiles/ms/icons/yellow-dot.png";
var orange_marker = "https://maps.google.com/mapfiles/ms/icons/orange-dot.png";
var red_marker = "https://maps.google.com/mapfiles/ms/icons/red-dot.png";
var blue_marker = "https://maps.google.com/mapfiles/ms/icons/blue-dot.png";

var database;
var problems;
var storage;
var images;

function initList() {
    database = firebase.database();
    problems = database.ref("Problems");
    storage = firebase.storage();
    images = storage.ref();

    problems.on("value", function(snapshot) {
        clearMarkers();
        $("#list").empty();
        snapshot.forEach(function(problems) {
            var val = problems.val();
            var latlng = new google.maps.LatLng(val.lat, val.llong)
            var ico;
            switch (val.rating) {
                case 1:
                    ico = yellow_marker;
                    break;
                case 2:
                    ico = orange_marker;
                    break;
                case 3:
                    ico = red_marker;
                    break;
            }

            var marker = new google.maps.Marker({
                position: latlng,
                icon: ico,
                title: val.id
            });
            marker.setMap(map);
            markers[val.id] = marker;

            marker.addListener("click", function() {
                var id = "." + this.title;
                $('#list').animate({
                    scrollTop: $(id).offset().top
                }, {
                    complete: function() {
                        $(id).effect("highlight", {color: "blue"}, 1450);
                    }
                }, 'slow');
            });

            var dist;
            var dist_num;
            if (loc) {
                dist_num = google.maps.geometry.spherical.computeDistanceBetween(latlng, loc_marker.position);
                dist = dist_num * 10;
                if (dist < 1000) {
                    dist = (Math.round(dist) / 10).toString() + " m away";
                } else {
                    dist = (Math.round(dist / 1000) / 10).toString() + " km away";
                }
            } else {
                dist = "Unknown Distance"
            }

            var imageRef = images.child("Problems/" + val.id + ".jpg");
            imageRef.getDownloadURL().then(function(url) {
                var id = url.split("F").pop().split(".")[0];
                $("#" + id).attr("src", url);
            });
			
			var i;
			switch(val.rating) {
				case 1:
					i = "one";
					break;
				case 2:
					i = "two";
					break;
				case 3:
					i = "three";
					break;
			}

            var link = "https://www.google.com/maps?saddr=My+Location&daddr=" + val.lat + "," + val.llong;
            var problem = '<li class="' + val.id + '" id="' + i + '">' +
                '<img class="img" id="' + val.id + '" />' +
                '<div class="distance" val="' + dist_num + '" ">' + dist + '</div>' +
                '<a class="navigate" href="' + link + '" target="_blank">Navigate</a>' + 
                '</li>';

            $("#list").append(problem);
        });

        $("#list li").sort(function(a, b) {
            return $(b).children(".distance").attr("val") < $(a).children(".distance").attr("val") ? 1 : -1;
        }).appendTo("#list");
    });
}

/**********************************MAP INITIALIZATION**********************************/
var map;
var loc;
var loc_marker;
var zoom = 18;
var markers = {};


function clearMarkers() {
    for (var i in markers) {
        markers[i].setMap(null);
    }
    markers = {};
}

function initMap(pos) {
    loc = pos;
    if (loc) {
        addYourLocationButton();
        loc_marker = new google.maps.Marker({
            map: map,
            position: pos,
            title: "Your Currennt Location",
            icon: blue_marker
        });
        map.panTo(pos);
        map.setZoom(zoom);

        initList();
    }
}

function init() {
    map = new google.maps.Map(document.getElementById("map"), {
        zoom: 11,
        center: {
            lat: 17.3850,
            lng: 78.4867
        }
    });

    $("#list").on("click", "li", function() {
        var id = $(this).attr("class");
        map.panTo(markers[id].getPosition());

        if (markers[id].getAnimation() != google.maps.Animation.BOUNCE) {
            markers[id].setAnimation(google.maps.Animation.BOUNCE);
        }
        setTimeout(function() {
            markers[id].setAnimation(null);
        }, 1450);

        map.setZoom(zoom);
    });

    $("#search_bar").on("focus", function() {
        this.select();
    });

    $("#log_out").button();
    $("#log_out").click(function() {
        firebase.auth().signOut().then(function() {

        }, function(err) {
            createDialog("Sign in Failed!", "An unkown error occurred. Please try again.", "");
        });
    });

    var hyderabadBounds = new google.maps.LatLngBounds(
        new google.maps.LatLng(17.209573, 78.161679),
        new google.maps.LatLng(17.603366, 78.688154));
    var searchInput = document.getElementById("search_bar");
    var options = {
        bounds: hyderabadBounds
    }
    var autocomplete = new google.maps.places.Autocomplete(searchInput, options);
    autocomplete.addListener("place_changed", function() {
        var place = autocomplete.getPlace();
        if(place.length === 0) {
            return;
        }
        if(!place.geometry) {
            console.log("No geometry!");
            return;
        }

        var bounds = new google.maps.LatLngBounds();
        if(place.geometry.viewport) {
            bounds.union(place.geometry.viewport);
        } else {
            bounds.extend(place.geometry.location);
        }
        map.fitBounds(bounds);
    });

    initList();
    getLocation(initMap);
}

function getLocation(func) {
    if (navigator.geolocation) {
        navigator.geolocation.getCurrentPosition(function(position) {
            var pos = {
                lat: position.coords.latitude,
                lng: position.coords.longitude
            };
            func(pos);
        }, function() {
            func(false);
        });
    } else {
        func(false);
    }
}


function addYourLocationButton() {
    var controlDiv = document.createElement('div');

    var firstChild = document.createElement('button');
    firstChild.style.backgroundColor = '#fff';
    firstChild.style.border = 'none';
    firstChild.style.outline = 'none';
    firstChild.style.width = '28px';
    firstChild.style.height = '28px';
    firstChild.style.borderRadius = '2px';
    firstChild.style.boxShadow = '0 1px 4px rgba(0,0,0,0.3)';
    firstChild.style.cursor = 'pointer';
    firstChild.style.marginRight = '10px';
    firstChild.style.padding = '0px';
    firstChild.title = 'Your Location';
    firstChild.id = 'your_location_button';
    controlDiv.appendChild(firstChild);

    var secondChild = document.createElement('div');
    secondChild.style.margin = '5px';
    secondChild.style.width = '18px';
    secondChild.style.height = '18px';
    secondChild.style.backgroundImage = 'url(https://maps.gstatic.com/tactile/mylocation/mylocation-sprite-1x.png)';
    secondChild.style.backgroundSize = '180px 18px';
    secondChild.style.backgroundPosition = '0px 0px';
    secondChild.style.backgroundRepeat = 'no-repeat';
    secondChild.id = 'you_location_img';
    firstChild.appendChild(secondChild);

    google.maps.event.addListener(map, 'dragend', function() {
        $('#you_location_img').css('background-position', '0px 0px');
    });

    firstChild.addEventListener('click', function() {
        var imgX = '0';
        var animationInterval = setInterval(function() {
            if (imgX == '-18') imgX = '0';
            else imgX = '-18';
            $('#you_location_img').css('background-position', imgX + 'px 0px');
        }, 500);
        if (navigator.geolocation) {
            navigator.geolocation.getCurrentPosition(function(position) {
                var latlng = new google.maps.LatLng(position.coords.latitude, position.coords.longitude);


                loc_marker.setPosition(latlng);
                map.panTo(latlng);
                map.setZoom(zoom);
                clearInterval(animationInterval);
                $('#you_location_img').css('background-position', '-144px 0px');
            });
        } else {
            clearInterval(animationInterval);
            $('#you_location_img').css('background-position', '0px 0px');
        }
    });

    controlDiv.index = 1;
    map.controls[google.maps.ControlPosition.RIGHT_BOTTOM].push(controlDiv);
}



function createDialog(title, message, icon) {
    var dialog = $("<div>" + message + "</div>").dialog({
        draggable: false,
        modal: true,
        buttons: {
        Ok: function() {
            $(this).dialog("close");
            }
        }
    });
    dialog.data( "uiDialog" )._title = function(title) {
        title.html(this.options.title);
    };
    dialog.dialog('option', 'title', '<span class="ui-icon ' + icon + '"></span> ' + title);
}
