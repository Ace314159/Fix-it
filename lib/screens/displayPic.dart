import 'package:flutter/material.dart';
import 'package:location/location.dart';
import 'dart:io';
import 'package:http/http.dart';

import 'package:fix_it/util/aws.dart';

class DisplayPicScreen extends StatefulWidget {
  final String imagePath;
  final LocationData location;

  const DisplayPicScreen(this.imagePath, this.location);

  @override
  DisplayPicScreenState createState() {
    return DisplayPicScreenState(imagePath, location);
  }
}

class DisplayPicScreenState extends State<DisplayPicScreen> {
  final String imagePath;
  final LocationData location;
  bool isLoading = false;

  DisplayPicScreenState(this.imagePath, this.location);

  void uploadImage(BuildContext context) async {
    setState(() {
     isLoading = true; 
    });
    String hash = await AWS.uploadPothole(File(imagePath), location);
    if(hash == "") {
      Navigator.popUntil(context, ModalRoute.withName(Navigator.defaultRouteName));
    } else {
      Navigator.pop(context, hash);
    }
  }
  
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Color(0),
      appBar: !isLoading ? AppBar(
        title: Text('Preview Pothole'),
        actions: <Widget>[
          IconButton(
            icon: Icon(Icons.check),
            onPressed: () {
              uploadImage(context);
            },
          ),
        ],
      ) : null,
      body: Stack(children: [
        Opacity(
          opacity: isLoading ? 0.5 : 1.0,
          child: Image.file(
            File(imagePath),
            fit: BoxFit.cover,
            height: double.infinity,
            width: double.infinity,
            alignment: Alignment.center,
          ),
        ),
        if(isLoading) Center(child: CircularProgressIndicator()),
      ])
    );
  }
}