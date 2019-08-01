import 'dart:convert';
import 'dart:io';
import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:location/location.dart';
import 'package:intl/intl.dart';
import 'package:geohash/geohash.dart';
import 'package:xml/xml.dart' as xml;

import 'keys.dart';


class AWS {
  static Hmac _hmac = Hmac(sha256, utf8.encode('AWS4${Keys.secretAccessKey}'));
  static DateFormat _dateFormat = DateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'");
  static DateFormat _amzDateFormat = DateFormat("yyyyMMdd'T'HHmmss'Z'");
  static DateFormat _dateKeyFormat = DateFormat('yyyyMMdd');
  static String bucket = 'pothole-images';
  static String region = 'ap-south-1';
  static String service = 's3';

  static Future<http.Response> _sendAWSReq(String method, String authority, String path, Map<String, String> queryParams,
                                      Map<String, String> extraHeaders, List<int> payload, String contentType) {
    DateTime curTime = DateTime.now().toUtc();
    String hashedPayload = sha256.convert(payload).toString();
    Uri uri = Uri.https(authority, path, queryParams);

    Map<String, String> headers = {
      'Host': '$bucket.s3.amazonaws.com',
      'Date': _dateFormat.format(curTime),
      'x-amz-date': _amzDateFormat.format(curTime),
      'x-amz-content-sha256': hashedPayload,
      'Content-Type': contentType,
      ...extraHeaders,
    };
    headers['Authorization'] =  _getAuthHeader(curTime, method, path, uri.query, headers, hashedPayload);
    
    switch(method) {
      case 'PUT':
        return http.put(uri, headers: headers, body: payload);
        break;
      case 'GET':
        return http.get(uri, headers: headers);
        break;
    }
  }

  static String _getAuthHeader(DateTime curTime, String httpMethod, String canonicalURI, String canonicalQueryStr,
                               Map<String, String> headers, String hashedPayload) {
    final String formattedTime = _dateKeyFormat.format(curTime);
    final String scope = '$formattedTime/$region/$service/aws4_request';
    final String credential = '${Keys.accessKeyID}/$scope';

    String signedHeaders = (headers.keys.toList(growable: false)..sort()).map((header) => header.toLowerCase()).join(';');
    String canonicalHeaders = _getCanonicalHeaders(headers);
    String canonicalReq = [httpMethod, canonicalURI, canonicalQueryStr, canonicalHeaders,
                           signedHeaders, hashedPayload].join('\n');
    String hashedCanonicalReq = sha256.convert(utf8.encode(canonicalReq)).toString();
    String strToSign = ['AWS4-HMAC-SHA256', headers['x-amz-date'], scope, hashedCanonicalReq].join('\n');

    final List<int> dateKey = _hmac.convert(utf8.encode(formattedTime)).bytes;
    final List<int> dateRegionKey = Hmac(sha256, dateKey).convert(utf8.encode(region)).bytes;
    final List<int> dateRegionServiceKey = Hmac(sha256, dateRegionKey).convert(utf8.encode(service)).bytes;
    final List<int> signingKey = Hmac(sha256, dateRegionServiceKey).convert(utf8.encode('aws4_request')).bytes;
    final String signature = Hmac(sha256, signingKey).convert(utf8.encode(strToSign)).toString();


    return 'AWS4-HMAC-SHA256 Credential=$credential,SignedHeaders=$signedHeaders,Signature=$signature';
  }

  static String _getCanonicalHeaders(Map<String, String> headers) {
    StringBuffer buffer = StringBuffer();

    for (final key in headers.keys.toList(growable: false)..sort()) {
      buffer.writeln('${key.toLowerCase()}:${headers[key]}');
    }
    return buffer.toString();
  }
  
  static Future<String> uploadPothole(File image, LocationData location) async {
    String hash = Geohash.encode(location.latitude, location.longitude);
    String fileName = Uri.encodeComponent(hash);
    List<int> fileBytes = image.readAsBytesSync();

    http.Response response = await _sendAWSReq('PUT', '$bucket.s3.amazonaws.com', '/$fileName.png',
                                               {}, {}, fileBytes, 'image/png');
    if(response.statusCode == 200) {
      return hash;
    } else {
      return "";
    }
  }

  static void getPotholes(void Function(String) addMarker) async {
    Map<String, String> queryParams = {
      'list-type': '2',
    };
    String response = (await _sendAWSReq('GET', '$bucket.s3.amazonaws.com', '/', queryParams, {}, [], 'text/plain')).body;
    xml.XmlDocument document = xml.parse(response);
    xml.XmlNode listBucketResult = document.lastChild;
    for(xml.XmlNode child1 in listBucketResult.children) {
      if(child1 is xml.XmlElement && child1.name.local == 'Contents') {
        for(xml.XmlNode child2 in child1.descendants) {
          if(child2 is xml.XmlElement && child2.name.local == 'Key') {
            String fileName = child2.text;
            addMarker(fileName.substring(0, fileName.lastIndexOf('.')));
            break;
          }
        }
      }
    }
  }
}