import 'dart:async';
import 'dart:developer';

import 'package:flutter/material.dart';
import 'package:camera/camera.dart';
import 'package:location/location.dart';
import 'package:path/path.dart' show join;
import 'package:path_provider/path_provider.dart';

import 'displayPic.dart';


class Camera extends StatefulWidget {
  final CameraDescription camera;
  final Location locationService;

  const Camera({
    Key key,
    @required this.camera,
    @required this.locationService,
  }) : super(key: key);

  @override
  CameraState createState() => CameraState();
}

class CameraState extends State<Camera> {
  bool isLoading = false;
  CameraController _controller;
  Future<void> _initializeControllerFuture;

  @override
  void initState() {
    super.initState();
    _controller = CameraController(widget.camera, ResolutionPreset.high, enableAudio: false);
    // TODO: Handle exception
    try {
      _initializeControllerFuture = _controller.initialize();
    } on CameraException catch(e) {
      print(e);
      debugger();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
      return Scaffold(
        appBar: AppBar(
          title: Text('Take a Picture'),
        ),
        backgroundColor: const Color(0),
        body: FutureBuilder<void>(
          future: _initializeControllerFuture,
          builder: (context, snapshot) {
            if(snapshot.connectionState == ConnectionState.done && !isLoading) {
              return CameraPreview(_controller);
            } else {
              return Center(child: CircularProgressIndicator());
            }
          },
        ),
        floatingActionButton: FloatingActionButton(
          child: Icon(Icons.camera_alt),
          onPressed: () async {
            setState(() {
             isLoading = true; 
            });
            try {
              await _initializeControllerFuture;

              final path = join(
                (await getTemporaryDirectory()).path,
                '${DateTime.now()}.png',
              );

              List<dynamic> data = await Future.wait([_controller.takePicture(path), widget.locationService.getLocation()]);
              LocationData locationData = data[1];

              isLoading = false;
              var hash = await Navigator.push(
                context,
                MaterialPageRoute(
                  builder: (context) => DisplayPicScreen(path, locationData),
                  fullscreenDialog: true,
                ),
              );
              if(hash is String) {
                Navigator.pop(context, hash);
              }
            } catch(e) {
              setState(() {
                isLoading = false;
              });
              print(e);
              debugger();
            }
          },
        ),
      );
  }
}
