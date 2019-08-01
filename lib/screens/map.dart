import 'dart:async';
import 'dart:developer';

import 'package:camera/camera.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:location/location.dart';
import 'package:google_maps_flutter/google_maps_flutter.dart';
import 'camera.dart';
import 'package:geohash/geohash.dart';
import 'dart:math';

import 'package:fix_it/util/aws.dart';


class Map extends StatefulWidget {
  @override
  State<Map> createState() => MapState();
}

class MapState extends State<Map> {
  Location _locationService = Location();
  Completer<GoogleMapController> _controller = Completer();
  Set<Marker> markers = {};

  static final CameraPosition _initialCamera = CameraPosition(
    target: LatLng(0, 0),
  );

  @override
  void initState() {
    super.initState();
    getMarkers();

    SystemChrome.setEnabledSystemUIOverlays([]);
  }

  void getMarkers() async {
    AWS.getPotholes((hash) {
      Point<double> p = Geohash.decode(hash);
      setState(() {
        markers.add(Marker(
          markerId: MarkerId(hash),
          position: LatLng(p.x, p.y),
        ));
      });
    });
  }
  
  void initPlatformState() async {
    await _locationService.changeSettings(accuracy: LocationAccuracy.HIGH, interval: 1000, distanceFilter: 0);

    try {
      bool _serviceEnabled = await _locationService.serviceEnabled();
      if(_serviceEnabled) {
        await _locationService.requestPermission();
        await moveToCurPos();
      }
    } on PlatformException catch(e) {
      print(e);
      debugger();
    }
  }

  Future<void> moveToCurPos() async {
    if(await _locationService.hasPermission()) {
      LocationData _l = await _locationService.getLocation();
      (await _controller.future).moveCamera(CameraUpdate.newCameraPosition(CameraPosition(
        target: LatLng(_l.latitude, _l.longitude),
        zoom: 16.0
      )));
    } else {
      // TODO: Handle no permission
    }
  }

  @override
  Widget build(BuildContext context) {
    return new Scaffold(
      body: GoogleMap(
        mapType: MapType.normal,
        myLocationEnabled: true,
        initialCameraPosition: _initialCamera,
        onMapCreated: (GoogleMapController controller) {
          if(!_controller.isCompleted) {
            _controller.complete(controller);
            moveToCurPos();
          }
        },
        markers: markers,
      ),
      floatingActionButton: Builder(
        builder: (context) =>
          FloatingActionButton.extended(
          onPressed: () => _takePic(context),
          label: Text('Take Picture'),
          icon: Icon(Icons.photo_camera),
          ),
      ),
    );
  }

  void _takePic(BuildContext context) async {
    final cameras = await availableCameras();
    var hash = await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => Camera(camera: cameras.first, locationService: _locationService,),
      ),
    );
    if(hash is String) {
      setState(() {
        Point<double> p = Geohash.decode(hash);
        markers.add(Marker(
          markerId: MarkerId(hash),
          position: LatLng(p.x, p.y),
        ));
      });
    }

    Scaffold.of(context).showSnackBar(SnackBar(
      content: Text('Successfully reported pothole'),
    ));
  }
}