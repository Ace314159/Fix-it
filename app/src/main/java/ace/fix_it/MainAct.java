package ace.fix_it;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.drawable.LayerDrawable;
import android.location.Location;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.RatingBar;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.UiSettings;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.UUID;

public class MainAct extends FragmentActivity implements OnMapReadyCallback, ResultCallback<LocationSettingsResult>,
        LocationListener {

    GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;

    FirebaseDatabase database = FirebaseDatabase.getInstance();
    DatabaseReference problemsDataRef;
    FirebaseStorage storage = FirebaseStorage.getInstance();
    StorageReference storageRef;
    StorageReference problemsStorageRef;

    LocationRequest locationRequest;
    LocationSettingsRequest locationSettingsRequest;

    FloatingActionButton fab;
    boolean googlePlayServices;
    int rating;
    String TAG;
    Location location = null;

    final int PERMISSIONS_REQUEST_CODE = 1;
    final int IMAGE_CAPTURE_REQUEST_CODE = 2;
    final String[] permissions = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        TAG = getString(R.string.TAG);

        problemsDataRef = database.getReference(getString(R.string.database_ref_dir));
        storageRef = storage.getReferenceFromUrl(getString(R.string.firebase_url));
        problemsStorageRef = storageRef.child(getString(R.string.storage_ref_dir));

        googlePlayServices = isGooglePlayServicesAvailable();

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(LocationServices.API)
                    .build();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSIONS_REQUEST_CODE);
        } else {
            init();
        }

        problemsDataRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                System.out.println(1);
                mMap.clear();
                for (DataSnapshot postSnapshot : dataSnapshot.getChildren()) {
                    HashMap<String, Object> h = (HashMap) postSnapshot.getValue();
                    BitmapDescriptor b;
                    switch (h.get("rating").toString()) {
                        case "1":
                            b = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW);
                            break;
                        case "2":
                            b = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE);
                            break;
                        case "3":
                            b = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED);
                            break;
                        default:
                            b = BitmapDescriptorFactory.defaultMarker();
                            break;
                    }
                    mMap.addMarker(new MarkerOptions()
                            .position(new LatLng((Double) h.get("lat"), (Double) h.get("llong")))
                            .icon(b));
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull final String permissions[], @NonNull int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST_CODE:
                int wrong = 0;
                if (grantResults.length > 1 && grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                    init();
                } else if (grantResults.length > 0 && grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    wrong = 1;
                }
                if (grantResults.length > 1 && grantResults[1] != PackageManager.PERMISSION_GRANTED) {
                    if (wrong == 1) {
                        wrong = 3;
                    } else {
                        wrong = 2;
                    }
                }
                switch (wrong) {
                    case 1:
                        new AlertDialog.Builder(MainAct.this)
                                .setTitle(R.string.location_permission_denied_title)
                                .setCancelable(false)
                                .setMessage(R.string.location_permission_denied_message)
                                .setPositiveButton(R.string.location_permission_denied_button_text, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        ActivityCompat.requestPermissions(MainAct.this, permissions, PERMISSIONS_REQUEST_CODE);
                                    }
                                })
                                .show();
                        break;
                    case 2:
                        new AlertDialog.Builder(MainAct.this)
                                .setTitle(R.string.camera_permission_denied_title)
                                .setCancelable(false)
                                .setMessage(R.string.camera_permission_denied_message)
                                .setPositiveButton(R.string.camera_permission_denied_button_text, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        ActivityCompat.requestPermissions(MainAct.this, permissions, PERMISSIONS_REQUEST_CODE);
                                    }
                                })
                                .show();
                        break;
                    case 3:
                        new AlertDialog.Builder(MainAct.this)
                                .setTitle(R.string.location_and_camera_permission_denied_title)
                                .setCancelable(false)
                                .setMessage(R.string.location_and_camera_permission_denied_message)
                                .setPositiveButton(R.string.location_and_camera_permission_denied_button_text, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        ActivityCompat.requestPermissions(MainAct.this, permissions, PERMISSIONS_REQUEST_CODE);
                                    }
                                })
                                .show();
                        break;
                }
                break;
        }
    }

    public void init() {
        createLocationRequest();
        buildLocationSettings();
        checkLocationSettings();

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                    startActivityForResult(takePictureIntent, IMAGE_CAPTURE_REQUEST_CODE);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == IMAGE_CAPTURE_REQUEST_CODE && resultCode == RESULT_OK) {
            final Intent d = data;

            View problemSelectionView = this.getLayoutInflater().inflate(R.layout.problem_selection, null);
            AlertDialog.Builder problemSelection = new AlertDialog.Builder(MainAct.this)
                    .setTitle(R.string.problem_selection_title)
                    .setCancelable(false)
                    .setView(problemSelectionView);

            ImageView imageView = (ImageView) problemSelectionView.findViewById(R.id.problem_pic);

            imageView.setImageBitmap((Bitmap) data.getExtras().get("data"));
            final RatingBar ratingBar = (RatingBar) problemSelectionView.findViewById(R.id.ratingBar);
            ratingBar.setOnRatingBarChangeListener(new RatingBar.OnRatingBarChangeListener() {
                @Override
                public void onRatingChanged(RatingBar ratingBar, float r, boolean fromUser) {
                    LayerDrawable stars = (LayerDrawable) ratingBar.getProgressDrawable();
                    rating = (int) r;
                    switch (rating) {
                        case 3:
                            stars.getDrawable(2).setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.red), PorterDuff.Mode.SRC_ATOP);
                            break;
                        case 2:
                            stars.getDrawable(2).setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.orange), PorterDuff.Mode.SRC_ATOP);
                            break;
                        case 1:
                            stars.getDrawable(2).setColorFilter(ContextCompat.getColor(getApplicationContext(), R.color.yellow), PorterDuff.Mode.SRC_ATOP);
                            break;
                    }
                }
            });

            problemSelection.setPositiveButton(R.string.problem_selection_positive_button_text, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    upload(d, rating, location);
                }
            });

            problemSelection.setNegativeButton(R.string.problem_selection_negative_button_text, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();
                }
            });
            AlertDialog dialog = problemSelection.create();
            dialog.show();
        }
    }

    public void upload(Intent d, int r, Location l) {
        final Location loc = l;
        if(loc == null) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.location_error_title)
                    .setMessage(R.string.location_error_message)
                    .setPositiveButton(R.string.location_error_positive_button, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {

                        }
                    })
                    .show();
            return;
        }
        final int rating = (r > 1) ? r : 1;
        final String id = UUID.randomUUID().toString();
        StorageReference imageRef = problemsStorageRef.child(id + ".jpg");

        final ProgressDialog progress = new ProgressDialog(MainAct.this);
        progress.setMessage("Uploading...");
        progress.setCancelable(false);
        progress.show();
        Bundle extras = d.getExtras();
        Bitmap image = (Bitmap) extras.get("data");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        image.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        byte[] data = baos.toByteArray();

        UploadTask uploadTask = imageRef.putBytes(data);
        uploadTask.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                progress.dismiss();
                new AlertDialog.Builder(MainAct.this)
                        .setTitle(R.string.upload_fail_title)
                        .setCancelable(false)
                        .setMessage(R.string.upload_fail_message)
                        .setPositiveButton(R.string.upload_fail_button_text, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .show();
            }
        });

        uploadTask.addOnSuccessListener(new OnSuccessListener<UploadTask.TaskSnapshot>() {
            @Override
            public void onSuccess(UploadTask.TaskSnapshot taskSnapshot) {
                Problem problem = new Problem(loc.getLatitude(), loc.getLongitude(), id, rating);
                problemsDataRef.child(id).setValue(problem);

                progress.dismiss();
                new AlertDialog.Builder(MainAct.this)
                        .setTitle(R.string.upload_success_title)
                        .setCancelable(false)
                        .setMessage(R.string.upload_success_message)
                        .setPositiveButton(R.string.upload_success_button_text, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .show();
            }
        });
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        UiSettings mapSettings = mMap.getUiSettings();
        mapSettings.setCompassEnabled(true);
        mMap.setMyLocationEnabled(true);
        mapSettings.setMyLocationButtonEnabled(true);
    }

    private boolean isGooglePlayServicesAvailable() {
        GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
        int status = googleApiAvailability.isGooglePlayServicesAvailable(this);
        if (status != ConnectionResult.SUCCESS) {
            if (googleApiAvailability.isUserResolvableError(status)) {
                googleApiAvailability.getErrorDialog(this, status, 2404).show();
            }
            return false;
        }
        return true;
    }


    @Override
    protected void onStart() {
        super.onStart();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mGoogleApiClient.disconnect();
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
                        mGoogleApiClient,
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
        try {
            LocationServices.FusedLocationApi.requestLocationUpdates(
                    mGoogleApiClient,
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
    public void onLocationChanged(Location loc) {
        location = loc;
    }
}
