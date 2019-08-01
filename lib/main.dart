import 'package:flutter/material.dart';

import 'screens/map.dart';


void main() => runApp(App());


class App extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: "Fix it!",
      home: Map(),
    );
  }
}
