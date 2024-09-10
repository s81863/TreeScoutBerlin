package de.geoit.treescoutberlin;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.common.util.concurrent.ListenableFuture;

import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.CustomZoomButtonsController;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraActivity extends AppCompatActivity implements SensorEventListener {
    private static final int REQUEST_CODE_PERMISSIONS = 10;
    private final String[] REQUIRED_PERMISSIONS = new String[]{"android.permission.CAMERA", "android.permission.ACCESS_FINE_LOCATION"};
    private static final String PREFS_NAME = "AppPrefs";
    private static final String KEY_SHOW_CALIBRATION_MESSAGE = "show_calibration_message";

    private PreviewView previewView;
    private TextView treeInfoOverlay, treeInfoOverlay2, info;

    private MapView map = null;
    Marker myLocationMarker;

    private List<Marker> foundTreeMarkers = new ArrayList<>();
    private Marker chosenTreeMarker;

    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private float[] rotationMatrix = new float[9];
    private float[] orientationAngles = new float[3];

    private Location currentLocation;
    private float currentBearing;

    private DataBaseHelper dataBaseHelper;

    private FusedLocationProviderClient fusedLocationProviderClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera_view);

        previewView = findViewById(R.id.cameraPreview);
        treeInfoOverlay = findViewById(R.id.treeInfoOverlay);
        treeInfoOverlay2 = findViewById(R.id.treeInfoOverlay2);
        info = findViewById(R.id.treeOverlay);

        if (allPermissionsGranted()) {
            startCamera();
            startLocationUpdates();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean showCalibrationMessage = prefs.getBoolean(KEY_SHOW_CALIBRATION_MESSAGE, true);

        map = (MapView) findViewById(R.id.mapView);
        map.setTileSource(TileSourceFactory.MAPNIK);

        map.getZoomController().setVisibility(CustomZoomButtonsController.Visibility.NEVER);
        map.setMultiTouchControls(true);
        map.getController().setZoom(14.0);
        map.getController().setCenter(new GeoPoint(52.45, 13.35));
        myLocationMarker = new Marker(map);
        myLocationMarker.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.location_icon_s, null));
        myLocationMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM - 0.1f);

        map.getOverlayManager().add(myLocationMarker);

        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        boolean gpsEnabled = manager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        if (!gpsEnabled) {
            showAlertMessageNoGps(new Runnable() {
                @Override
                public void run() {
                    if (showCalibrationMessage) {
                        showCalibrationAlert();
                    }
                }
            });
        } else {
            if (showCalibrationMessage) {
                showCalibrationAlert();
            }
        }

        dataBaseHelper = new DataBaseHelper(this);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        ImageButton closeButton = findViewById(R.id.bt_close);
        FrameLayout treeOverlayContainer = findViewById(R.id.treeOverlayContainer);

        closeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                treeOverlayContainer.setVisibility(View.GONE);
            }
        });


        ImageButton backToMainActivityButton = findViewById(R.id.bt_back_to_main);
        backToMainActivityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        ImageButton buttonScan = findViewById(R.id.bt_scan);
        buttonScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                Toast.makeText(CameraActivity.this, "Suche...", Toast.LENGTH_SHORT).show();
                if (currentLocation != null) {
                    String query = locationBasedQuery(currentLocation);
                    List<Tree> treeInfo = identifiedTree(query, currentLocation, currentBearing);

                    if (treeInfo != null && !treeInfo.isEmpty()) {

                        Tree bestTree = treeInfo.get(0);
                        treeOverlayContainer.setVisibility(View.VISIBLE);
                        info.setText(bestTree.toString());
                        info.setVisibility(View.VISIBLE);

                        if (chosenTreeMarker != null) {
                            map.getOverlayManager().remove(chosenTreeMarker);
                        }

                        chosenTreeMarker = new Marker(map);
                        chosenTreeMarker.setPosition(bestTree.getLocation());
                        chosenTreeMarker.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.chosen_tree_icon_s, null));
                        chosenTreeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                        map.getOverlayManager().add(chosenTreeMarker);
                    }
                } else {
                    Toast.makeText(CameraActivity.this, "Der Standort ist noch nicht bekannt.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera();
                startLocationUpdates();
            } else {
                Toast.makeText(this, "Permissions not granted by the user.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreview(ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        cameraProvider.bindToLifecycle(this, cameraSelector, preview);
    }

    private String locationBasedQuery(Location location) {
        double latMax = location.getLatitude() + 0.00015;
        double latMin = location.getLatitude() - 0.00015;
        double lngMax = location.getLongitude() + 0.0002;
        double lngMin = location.getLongitude() - 0.0002;

        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("SELECT * FROM baeume WHERE y <= ").append(latMax)
                .append(" AND y >= ").append(latMin)
                .append(" AND x <= ").append(lngMax)
                .append(" AND x >= ").append(lngMin);

        return queryBuilder.toString();
    }


    private List<Tree> identifiedTree(String query, Location deviceLocation, float deviceBearing) {
        SQLiteDatabase db = dataBaseHelper.getWritableDatabase();
        List<Tree> trees = new ArrayList<>();

        // Vorherige Marker entfernen und Liste leeren
        for (Marker marker : foundTreeMarkers) {
            map.getOverlayManager().remove(marker);
        }
        foundTreeMarkers.clear();

        Cursor cur = db.rawQuery(query, null);

        if (cur != null && cur.moveToFirst()) {
            do {
                int columnIndexX = cur.getColumnIndex("x");
                int columnIndexY = cur.getColumnIndex("y");
                int columnIndexArtDtsch = cur.getColumnIndex("art_dtsch");
                int columnIndexPflanzjahr = cur.getColumnIndex("pflanzjahr");
                int columnIndexStandalter = cur.getColumnIndex("standalter");
                int columnIndexArtBot = cur.getColumnIndex("art_bot");
                int columnIndexStammumfg = cur.getColumnIndex("stammumfg");
                int columnIndexBaumhoehe = cur.getColumnIndex("baumhoehe");
                int columnIndexKronedurch = cur.getColumnIndex("kronedurch");

                if (columnIndexX != -1 && columnIndexY != -1) {
                    double lon = cur.getDouble(columnIndexX);
                    double lat = cur.getDouble(columnIndexY);
                    Location treeLocation = new Location("locationTree");
                    treeLocation.setLatitude(lat);
                    treeLocation.setLongitude(lon);

                    double distance = deviceLocation.distanceTo(treeLocation);

                    float treeBearing = deviceLocation.bearingTo(treeLocation);

                    // Richtungswerte normalisieren
                    treeBearing = (treeBearing + 360) % 360;
                    deviceBearing = (deviceBearing + 360) % 360;

                    // Richtungsabweichung berechnen und so normalisieren, dass die Abweichung, egal ob nach links oder
                    // rechts denselben Wert hat
                    float bearingDifference = Math.abs(treeBearing - deviceBearing);
                    if (bearingDifference > 180) {
                        bearingDifference = 360 - bearingDifference;
                    }

                    // Neuen Marker für einen Baum erstellen
                    Marker foundTree = new Marker(map);
                    foundTree.setPosition(new GeoPoint(lat, lon));
                    foundTree.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.found_tree_icon_s, null));
                    foundTree.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER);
                    map.getOverlayManager().add(foundTree);
                    foundTreeMarkers.add(foundTree);

                    // Wenn das Gerät innerhalb von 45° zu einem Baum ausgerichtet ist
                    if (bearingDifference < 45) {
                        // Score aus Abstand und Richtungsabweichung berechnen, mit mehr Gewicht auf Abstand
                        double score = (distance * 2) + (bearingDifference);

                        // Baumobjekt erstellen und der Liste hinzufügen
                        Tree tree = new Tree(
                                cur.getString(columnIndexArtDtsch).replace("\"", ""),
                                cur.getString(columnIndexArtBot).replace("\"", ""),
                                cur.getInt(columnIndexPflanzjahr),
                                cur.getInt(columnIndexStandalter),
                                new GeoPoint(cur.getDouble(columnIndexY), cur.getDouble(columnIndexX)),
                                cur.getDouble(columnIndexStammumfg),
                                cur.getDouble(columnIndexBaumhoehe),
                                cur.getDouble(columnIndexKronedurch),
                                distance,
                                treeBearing,
                                bearingDifference,
                                score
                        );

                        trees.add(tree);

                    }
                }
            } while (cur.moveToNext());
        }

        if (cur != null) {
            cur.close();
        }

        db.close();

        // List nach Score sortieren (geringster Score zuerst)
        Collections.sort(trees, Comparator.comparingDouble(Tree::getScore));

        // Wenn keine Bäume gefunden wurden, entsprechende Nachricht ausgeben
        if (trees.isEmpty()) {
            Toast.makeText(this, "In dieser Richtung konnten keine Bäume gefunden werden.", Toast.LENGTH_SHORT).show();
            return null;
        }

        return trees;
    }

    // Klasse für Objekt Tree
    private class Tree {
        private String speciesGerman;
        private String speciesBotanical;
        private int yearPlanted;
        private int age;
        private GeoPoint location;
        private double trunkCircumference;
        private double treeHeight;
        private double crownDiameter;
        private double distance;
        private float treeBearing;
        private float bearingDifference;
        private double score;

        public Tree(String speciesGerman, String speciesBotanical, int yearPlanted, int age, GeoPoint location,
                    double trunkCircumference, double treeHeight, double crownDiameter, double distance, float treeBearing,
                    float bearingDifference, double score) {
            this.speciesGerman = speciesGerman;
            this.speciesBotanical = speciesBotanical;
            this.yearPlanted = yearPlanted;
            this.age = age;
            this.location = location;
            this.trunkCircumference = trunkCircumference;
            this.treeHeight = treeHeight;
            this.crownDiameter = crownDiameter;
            this.distance = distance;
            this.treeBearing = treeBearing;
            this.bearingDifference = bearingDifference;
            this.score = score;
        }

        public double getScore() {
            return score;
        }

        public GeoPoint getLocation() {
            return location;
        }

        @Override
        public String toString() {
            return speciesGerman + "\n" +
                    "Art (Botanisch): " + speciesBotanical + "\n" +
                    "Pflanzjahr: " + yearPlanted + "\n" +
                    "Standalter: " + age + " Jahre" + "\n" +
                    "Stammdurchmesser: " + trunkCircumference + " cm\n" +
                    "Baumhöhe: " + treeHeight + " m\n" +
                    "Kronendurchmesser: " + crownDiameter + " m\n";
        }
    }

    // Alert Dialog zum Kalibrieren der Sensoren
    private void showCalibrationAlert() {

        LayoutInflater inflater = getLayoutInflater();
        View dialogLayout = inflater.inflate(R.layout.dialog_calibration, null);

        final CheckBox dontShowAgain = dialogLayout.findViewById(R.id.checkbox_dont_show_again);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Sensoren Kalibrieren");
        builder.setMessage("Um die genaue Ausrichtung Ihres Geräts zu bestimmen, kalibrieren Sie bitte die Sensoren, " +
                "indem Sie das Gerät 8-förmig bewegen und halten Sie es allzeit von stärkeren Magnetfeldern fern (z.B. Verschluss einer Handyhülle)");
        builder.setView(dialogLayout); // Set the custom layout

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (dontShowAgain.isChecked()) {
                    // Save preference to not show the message again
                    SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                    editor.putBoolean(KEY_SHOW_CALIBRATION_MESSAGE, false);
                    editor.apply();
                }
                dialog.dismiss();
            }
        });

        builder.setCancelable(false); // Makes the dialog non-dismissible by tapping outside of it
        builder.show();
    }

    // Alert Dialog zum Aktivieren der GPS-Funktion
    private void showAlertMessageNoGps(final Runnable onDismissAction) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage("Bitte aktivieren Sie GPS, wenn Sie diese Funktion nutzen möchten.")
                .setCancelable(false)
                .setPositiveButton("GPS Aktivieren", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        if (onDismissAction != null) {
                            onDismissAction.run();
                        }
                    }
                })
                .setNegativeButton("Schließen", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialogInterface.cancel();
                        finish();
                    }
                });
        final AlertDialog dialog = builder.create();
        dialog.show();

    }

    private void startLocationUpdates() {
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        LocationRequest locationRequest = LocationRequest.create();
        locationRequest.setInterval(1000);
        locationRequest.setFastestInterval(500);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            return;
        }
        fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
    }

    private LocationCallback locationCallback = new LocationCallback() {
        @Override
        public void onLocationResult(LocationResult locationResult) {
            super.onLocationResult(locationResult);
            Location location = locationResult.getLastLocation();
            if (location != null) {

                //updateLocationUI(location);
                Log.d("Loc:", location.toString());
                currentLocation = location;
                myLocationMarker.setPosition(new GeoPoint(location.getLatitude(), location.getLongitude()));
                map.getController().setCenter(new GeoPoint(location.getLatitude(), location.getLongitude()));
                map.getController().setZoom(20.5d);
            }
        }
    };

    /* Nur zum Testen benötigt
    private void updateLocationUI(Location location) {
        String locationText = "Lat: " + location.getLatitude() + ", Lng: " + location.getLongitude();
        treeInfoOverlay2.setText(locationText);
        treeInfoOverlay2.setVisibility(View.VISIBLE);
    }
    */

    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        fusedLocationProviderClient.removeLocationUpdates(locationCallback);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientationAngles);

            float azimuth = (float) Math.toDegrees(orientationAngles[0]);
            azimuth = (azimuth + 360) % 360;
            currentBearing = azimuth;
            myLocationMarker.setRotation(-azimuth);
            map.invalidate();
            //String bearingText = "Bearing: " + String.format("%.2f", azimuth) + "°";
            //treeInfoOverlay.setText(bearingText);
            //treeInfoOverlay.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }


}




