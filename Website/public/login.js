var user;
firebase.initializeApp({
    apiKey: " AIzaSyC-le5lTljaHlZ6dwv4dOCKGyc-DnO8Ing",
    authDomain: "fix-it-1.firebaseapp.com"
});

$(document).ready(function() {
    $("#background").slick({
        autoplay: true,
        autoplaySpeed: 2000,
        fade: true,
        infinite: true,
        arrows: false,
    });

    var marginLeft = ($(window).width() / 2) - 200;
    var marginTop = ($(window).height() / 2) - 100;
    $("#email_box").css({ "margin-left": marginLeft, "margin-top": marginTop });
    $("#password_box").css({ "margin-left": marginLeft, "margin-top": marginTop + 60 });
    $("#log_in").css({ "margin-left": marginLeft, "margin-top": marginTop + 120 })

    $("#log_in").click(function() {
        firebase.auth().signInWithEmailAndPassword($("#email_box").val().trim(), $("#password_box").val().trim()).catch(function(err) {
            var title;
            var message;
            switch(err.code) {
                case "auth/invalid-email":
                case "auth/user-disabled":
                    title = "Invalid Email";
                    message = "Your email is not valid!";
                    break;
                case "auth/user-not-found":
                case "auth/wrong-password":
                    title = "Invalid Combination";
                    message = "Your email and password combination is invalid!"
                    break;
            }

            createDialog(title, message, "ui-icon-alert");
        });
    });

    $("#email_box").keypress(function(e) {
        if(e.keyCode === 13) {
            $("#log_in").click();
            return false;
        }
    });
    $("#password_box").keypress(function(e) {
        if(e.keyCode === 13) {
            $("#log_in").click();
            return false;
        }
    });
});

firebase.auth().onAuthStateChanged(function(u) {
    user = u;
    if (user) {
        window.location.replace("index.html");
    }
});

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
