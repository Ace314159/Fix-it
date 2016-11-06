package ace.fixit;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.*;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.*;
import com.google.firebase.storage.FileDownloadTask;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Sites extends AppCompatActivity implements GoogleApiClient.ConnectionCallbacks,
		GoogleApiClient.OnConnectionFailedListener, ResultCallback<LocationSettingsResult>,
		LocationListener, OnMapReadyCallback, GoogleMap.OnMarkerClickListener,
		SensorEventListener {
	static final int REQUEST_IMAGE_CAPTURE = 100;
	private static final int SHAKE_THRESHOLD = 17; // m/S**2
	private static final int MIN_TIME_BETWEEN_SHAKES_MILLISECS = 1000;
	static App app;
	static String TAG;
    static Bundle SIS;
	static File sitePhoto;
	static String sitePhotoName = "";
	static FirebaseStorage storage = FirebaseStorage.getInstance();
	static StorageReference storageRef = storage.getReferenceFromUrl("gs://fix-it-1.appspot.com");
	static FirebaseDatabase database = FirebaseDatabase.getInstance();
	static DatabaseReference databaseRef = database.getReference("Sites");
	static Collection<Object> sites;
	static GoogleApiClient googleApiClient;
	static Location location;
	static LocationRequest locationRequest;
	static LocationSettingsRequest locationSettingsRequest;
	static Uri downloadUri;
	static Uri photoUri;
	static boolean gpsOn;
	static boolean googlePlayServices;
	static boolean firstInit;
	static boolean imagesDownloaded;
	static MapView mapView;
	static GoogleMap map;
	String imageName;
	File image;
	Bitmap bit;
	private long mLastShakeTime;
	private SensorManager sensorMgr;
	private int authenticated;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, 12);
		}

        app = (App) getApplication();
        TAG = app.getTag();
        authenticated = app.isAuthenticated();
        firstInit = false;
        setContentView(R.layout.activity_sites);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        sensorMgr = (SensorManager) getSystemService(SENSOR_SERVICE);

        Sensor accelerometer = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if(accelerometer != null) {
            sensorMgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }

        googlePlayServices = isGooglePlayServicesAvailable();

        if(googleApiClient == null) {
            googleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(LocationServices.API)
                    .build();
        }
        mapView = (MapView) findViewById(R.id.map);
        mapView.onCreate(SIS);
        SIS = savedInstanceState;
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            create();
        }
	}

    private void create() {
        createLocationRequest();
        buildLocationSettings();
        checkLocationSettings();
        mapView.getMapAsync(this);
        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent takePicture = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if(takePicture.resolveActivity(getPackageManager()) != null) {
                    File photoFile = null;
                    gpsOn = true;
                    try {
                        photoFile = createImageFile();
                    } catch(IOException e) {
                        new AlertDialog.Builder(Sites.this)
                                .setTitle("File Error")
                                .setMessage("Fix it! cannot save the image file. Please allow Fix it! to save files.")
                                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {

                                    }
                                })
                                .setIcon(R.drawable.ic_warning)
                                .show();

                    }
                    if(photoFile != null) {
                        photoUri = FileProvider.getUriForFile(Sites.this, "ace.android.fileprovider", photoFile);
                        takePicture.putExtra(MediaStore.EXTRA_OUTPUT, photoUri);
                        takePicture.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        List<ResolveInfo> resInfoList = Sites.this.getPackageManager().queryIntentActivities(takePicture, PackageManager.MATCH_DEFAULT_ONLY);
                        for(ResolveInfo resolveInfo : resInfoList) {
                            String packageName = resolveInfo.activityInfo.packageName;
                            Sites.this.grantUriPermission(packageName, photoUri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        }
                        if(takePicture.resolveActivity(getPackageManager()) != null) {
                            startActivityForResult(takePicture, REQUEST_IMAGE_CAPTURE);
                        }
                    } else if(gpsOn) {
                        new AlertDialog.Builder(Sites.this)
                                .setTitle("Unexpected Error!")
                                .setMessage("Please make sure Fix it! can use your camera and try again.")
                                .setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialogInterface, int i) {

                                    }
                                })
                                .setIcon(R.drawable.ic_warning)
                                .show();
                    }
                }
            }
        });

        databaseRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Map<String, Object> pl = (HashMap<String, Object>) dataSnapshot.getValue();
                try {
                    sites = pl.values();
                } catch(NullPointerException e) {
                    Log.i(TAG, "No sites!");
                }
                initMap();
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
                Log.i(TAG, "Database Error: " + databaseError.getCode());
            }
        });
    }

	private void initMap() {
		downloadImages();
		final String state = Environment.getExternalStorageState();
		if(Environment.MEDIA_MOUNTED.equals(state)) {
			String path = Environment.getExternalStorageDirectory().toString() + "/Android/data/ace.fixit/files/Pictures";
			String[] locations = new File(path).list();
			if(locations != null) {
				for(int i = 0; i < locations.length; i++) {
					locations[i] = locations[i].substring(0, locations[i].lastIndexOf("."));
					try {
						double lat = Double.parseDouble(locations[i].split(",")[0]);
						double longi = Double.parseDouble(locations[i].split(",")[1]);
						map.addMarker(new MarkerOptions().position(new LatLng(lat, longi)).title(locations[i]));
					} catch(NumberFormatException e) {
						e.printStackTrace();
					}
				}
			}
		} else {
			new AlertDialog.Builder(Sites.this)
					.setTitle("Missing SD Card!")
					.setMessage("Please mount your SD card and restart the app.")
					.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {

						}
					})
					.setIcon(R.drawable.ic_warning)
					.show();
		}
	}

	@Override
	public void onMapReady(GoogleMap googleMap) {
		map = googleMap;
		map.setOnMarkerClickListener(this);
		if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, 12);
		}
		map.getUiSettings().setMyLocationButtonEnabled(true);
		map.setMyLocationEnabled(true);
		initMap();
	}

	private File createImageFile() throws IOException {
		ProgressDialog progress = new ProgressDialog(this);
		progress.setTitle("Obtaining Location");
		progress.setMessage("Please wait while we find your location...");
		progress.show();
		while(true) {
			try {
				location.getLatitude();
				break;
			} catch(NullPointerException e1) {
				try {
					Thread.sleep(100);
				} catch(InterruptedException e) {
					e.printStackTrace();
				}
			}
		}
		progress.dismiss();
		String latitude = Double.toString(location.getLatitude());
		String longitude = Double.toString(location.getLongitude());

		String imageFileName = latitude + "," + longitude;
		File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
		File image = new File(storageDir, imageFileName + ".jpg");
		if(!image.createNewFile()) {
			new AlertDialog.Builder(Sites.this)
					.setTitle("Could Not Create File!")
					.setMessage("Please clear app data and try again.")
					.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
						@Override
						public void onClick(DialogInterface dialogInterface, int i) {

						}
					})
					.setIcon(R.drawable.ic_warning)
					.show();
		}
		sitePhoto = image;
		sitePhotoName = image.getName();
		Log.i(TAG, sitePhoto.getAbsolutePath());
		return image;
	}

	private void downloadImages() {
		File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
		for(File f : storageDir.listFiles()) {
			if(!f.delete()) {
				new AlertDialog.Builder(Sites.this)
						.setTitle("Cannot Access File!")
						.setMessage("Please allow Fix it! to access your files and clear the app data.")
						.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {

							}
						})
						.setIcon(R.drawable.ic_warning)
						.show();
			}
		}

		if(sites == null) {
			imagesDownloaded = false;
		} else {
			for(Object imageName : sites) {
				try {
					File image = new File(storageDir, imageName.toString());
					if(!image.createNewFile()) {
						new AlertDialog.Builder(Sites.this)
								.setTitle("Could Not Create File!")
								.setMessage("Please clear app data and try again.")
								.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialogInterface, int i) {

									}
								})
								.setIcon(R.drawable.ic_warning)
								.show();
					}
				} catch(IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == REQUEST_IMAGE_CAPTURE) {
			if(resultCode == RESULT_OK) {
				StorageReference imageRef = storageRef.child(photoUri.getLastPathSegment());
				UploadTask uploadTask = imageRef.putFile(photoUri);
				ProgressDialog progress = new ProgressDialog(Sites.this);
				progress.setTitle("Uploading");
				progress.setMessage("Please wait while your image is uploading...");
				final ProgressDialog progressF = progress;
				progressF.show();

				uploadTask.addOnFailureListener(new OnFailureListener() {
					@Override
					public void onFailure(@NonNull Exception e) {
						progressF.dismiss();
						new AlertDialog.Builder(Sites.this)
								.setTitle("Upload Failed!")
								.setMessage("Your image upload has failed. Please check your internet connection.")
								.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialogInterface, int i) {

									}
								})
								.setIcon(R.drawable.ic_warning)
								.show();
					}
				});

				uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
					@Override
					public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
						progressF.dismiss();
						downloadUri = taskSnapshot.getDownloadUrl();
						DatabaseReference dataRef = database.getReference(sitePhotoName.substring(0, sitePhotoName.lastIndexOf(".")).replace(".", "-"));
						dataRef.setValue(sitePhotoName);
						new AlertDialog.Builder(Sites.this)
								.setTitle("Upload Successful!")
								.setMessage("Your image upload has succeeded.")
								.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialogInterface, int i) {

									}
								})
								.setIcon(R.drawable.ic_done)
								.show();
						initMap();
					}
				});
			} else if(resultCode == RESULT_CANCELED) {
				new AlertDialog.Builder(Sites.this)
						.setTitle("Image Capture Cancelled!")
						.setMessage("Your image has not been saved.")
						.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {

							}
						})
						.setIcon(R.drawable.ic_warning)
						.show();
				try {
					if(!new File(sitePhoto.getCanonicalPath()).delete()) {
						new AlertDialog.Builder(Sites.this)
								.setTitle("Cannot Access File!")
								.setMessage("Please allow Fix it! to access your files and clear the app data.")
								.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
									@Override
									public void onClick(DialogInterface dialogInterface, int i) {

									}
								})
								.setIcon(R.drawable.ic_warning)
								.show();
					}
				} catch(IOException e) {
					e.printStackTrace();
				}
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if(requestCode == 12) {
            if(grantResults[0] == 0) {
                create();
            }
        }
    }

	private boolean isGooglePlayServicesAvailable() {
		GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
		int status = googleApiAvailability.isGooglePlayServicesAvailable(this);
		if(status != ConnectionResult.SUCCESS) {
			if(googleApiAvailability.isUserResolvableError(status)) {
				googleApiAvailability.getErrorDialog(this, status, 2404).show();
			}
			return false;
		}
		return true;
	}

	@Override
	public void onConnected(@Nullable Bundle bundle) {
		Log.i(TAG, "Google API Client Connected");
	}

	@Override
	public void onConnectionSuspended(int i) {

	}

	@Override
	public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

	}

	protected void createLocationRequest() {
		locationRequest = new LocationRequest();
		locationRequest.setInterval(10000);
		locationRequest.setFastestInterval(5000);
		locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
	}

	protected void buildLocationSettings() {
		LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder();
		builder.addLocationRequest(locationRequest);
		locationSettingsRequest = builder.build();
	}

	protected void checkLocationSettings() {
		PendingResult<LocationSettingsResult> result =
				LocationServices.SettingsApi.checkLocationSettings(
						googleApiClient,
						locationSettingsRequest
				);
		result.setResultCallback(this);
	}

	@Override
	public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
		final Status status = locationSettingsResult.getStatus();
		switch(status.getStatusCode()) {
			case LocationSettingsStatusCodes.SUCCESS:
				Log.i(TAG, "All location settings are satisfied.");
				startLocationUpdates();
				break;
			case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
				Log.i(TAG, "Location settings are not satisfied. Show the user a dialog to" +
						"upgrade location settings ");

				try {
					status.startResolutionForResult(this, 0x1);
				} catch(IntentSender.SendIntentException e) {
					Log.i(TAG, "PendingIntent unable to execute request.");
				}
				break;
			case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
				Log.i(TAG, "Location settings are inadequate, and cannot be fixed here. Dialog " +
						"not created.");
				break;
		}
	}

	protected void startLocationUpdates() {
		if(ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			ActivityCompat.requestPermissions(this, new String[] { Manifest.permission.ACCESS_FINE_LOCATION }, 12);
		}
        try {
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    googleApiClient,
                    locationRequest,
                    this
            ).setResultCallback(new ResultCallback<Status>() {
                @Override
                public void onResult(@NonNull Status status) {

                }
            });
        } catch(NullPointerException e) {
            e.printStackTrace();
        }
	}

	@Override
	public void onPause() {
		super.onPause();
		firstInit = false;
		mapView.onPause();
		if(googleApiClient.isConnected()) {
			LocationServices.FusedLocationApi.removeLocationUpdates(googleApiClient, this);
			googleApiClient.disconnect();
		}
		sensorMgr.unregisterListener(this);
	}

	@Override
	public void onResume() {
		super.onResume();
		authenticated = app.isAuthenticated();
		mapView.onResume();
		googleApiClient.connect();
		if(googleApiClient.isConnected()) {
            startLocationUpdates();
		}
		Sensor accelerometer = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		if(accelerometer != null) {
			sensorMgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		}
	}

	protected void onStart() {
		googleApiClient.connect();
		super.onStart();
		Sensor accelerometer = sensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
		if(accelerometer != null) {
			sensorMgr.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
		}
	}

	protected void onStop() {
		googleApiClient.disconnect();
		sensorMgr.unregisterListener(this);
		super.onStop();
	}

	@Override
	public void onLocationChanged(Location l) {
		firstInit = true;
		double cLat = l.getLatitude();
		double cLong = l.getLongitude();
		map.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(cLat, cLong)));
		location = l;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		mapView.onSaveInstanceState(outState);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		mapView.onDestroy();
	}

	@Override
	public void onLowMemory() {
		super.onLowMemory();
		mapView.onLowMemory();
	}

	@Override
	public boolean onMarkerClick(Marker marker) {
		if(authenticated == 1) {
			imageName = marker.getTitle() + ".jpg";
			downloadImage(marker.getPosition());
		}
		return true;
	}

	private void downloadImage(LatLng markerL) {
		final LatLng markerLocation = markerL;
		image = new File(Environment.getExternalStorageDirectory().toString() + "/Android/data/ace.fixit/files/Pictures/" + imageName);
		StorageReference imageRef = FirebaseStorage.getInstance().getReferenceFromUrl("gs://fix-it-1.appspot.com").child(imageName);
		ProgressDialog progress = new ProgressDialog(this);
		progress.setTitle("Downloading Image");
		progress.setMessage("Please wait while we download the image...");
		final ProgressDialog progressF = progress;
		progressF.show();
		imageRef.getFile(image).addOnFailureListener(new OnFailureListener() {
			@Override
			public void onFailure(@NonNull Exception e) {
				progressF.dismiss();
				new AlertDialog.Builder(Sites.this)
						.setTitle("Could Not Download Files!")
						.setMessage("Please check your internet connection and try again.")
						.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
							@Override
							public void onClick(DialogInterface dialogInterface, int i) {

							}
						})
						.setIcon(R.drawable.ic_warning)
						.show();
			}
		}).addOnSuccessListener(new OnSuccessListener<FileDownloadTask.TaskSnapshot>() {
			@Override
			public void onSuccess(FileDownloadTask.TaskSnapshot taskSnapshot) {
				try {
					Thread.sleep(1000);
				} catch(InterruptedException e) {
					e.printStackTrace();
				}
				progressF.dismiss();
				bit = BitmapFactory.decodeFile(image.getAbsolutePath());

				AlertDialog.Builder builder = new AlertDialog.Builder(Sites.this);
				builder.setPositiveButton("Confirm Navigation", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
						String sLat = String.valueOf(location.getLatitude());
						String sLong = String.valueOf(location.getLongitude());
						String dLat = String.valueOf(markerLocation.latitude);
						String dLong = String.valueOf(markerLocation.longitude);
						final Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://maps.google.com/maps?" + "saddr=" + sLat + "," + sLong + "&daddr=" + dLat + "," + dLong));
						startActivity(intent);
					}
				});
				builder.setNegativeButton("Cancel Navigation", new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});
				AlertDialog dialog = builder.create();
				LayoutInflater inflater = getLayoutInflater();
				View dialogLayout = inflater.inflate(R.layout.dialog_navigation_confirmation, null);
				((ImageView) dialogLayout.findViewById(R.id.siteImage)).setImageBitmap(bit);
				dialog.setView(dialogLayout);
				dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

				dialog.show();
			}
		});
	}

	@Override public void onSensorChanged(SensorEvent event) {
		if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			long curTime = System.currentTimeMillis();
			if((curTime - mLastShakeTime) > MIN_TIME_BETWEEN_SHAKES_MILLISECS) {

				float x = event.values[0];
				float y = event.values[1];
				float z = event.values[2];

				double acceleration = Math.sqrt(Math.pow(x, 2) +
						Math.pow(y, 2) +
						Math.pow(z, 2)) - SensorManager.GRAVITY_EARTH;

				if(acceleration > SHAKE_THRESHOLD) {
					mLastShakeTime = curTime;
					if(authenticated == 0) {
						startActivity(new Intent(Sites.this, Authentication.class));
					}
				}
			}
		}
	}

	@Override public void onAccuracyChanged(Sensor sensor, int i) {

	}
}
