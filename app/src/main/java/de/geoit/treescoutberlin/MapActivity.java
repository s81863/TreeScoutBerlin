package de.geoit.treescoutberlin;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import org.osmdroid.bonuspack.clustering.RadiusMarkerClusterer;
import org.osmdroid.bonuspack.utils.BonusPackHelper;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polygon;


import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MapActivity extends AppCompatActivity {
    private final int REQUEST_PERMISSIONS_REQUEST_CODE = 1;
    private MapView map = null;

    private List<Polygon> listOfPolygons = new ArrayList<>();
    private List<Marker> currentMarkers = new ArrayList<>();
    private RadiusMarkerClusterer currentMarkerClusterer;

    private Polygon clickedPolygon = null;
    private Paint originalOutlinePaint = null;
    private Paint originalFillPaint = null;

    ImageButton filterButton;

    private DataBaseHelper dataBaseHelper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Context ctx = getApplicationContext();
        Configuration.getInstance().load(ctx, PreferenceManager.getDefaultSharedPreferences(ctx));

        setContentView(R.layout.activity_main);

        map = (MapView) findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK);


        map.setMultiTouchControls(true);
        map.getController().setZoom(14.0);
        map.getController().setCenter(new GeoPoint(52.45, 13.35));


        requestPermissionsIfNecessary(new String[] {
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_NETWORK_STATE,
                Manifest.permission.ACCESS_WIFI_STATE,
                android.Manifest.permission.INTERNET,
        });

        ImageButton backToMainActivityButton = findViewById(R.id.bt_back_to_main);
        backToMainActivityButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });

        filterButton = findViewById(R.id.bt_filter);
        filterButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MapActivity.this, SettingsActivity.class);
                startActivity(intent);
            }
        });

        dataBaseHelper = new DataBaseHelper(this);

        loadPlanungsraumPolygons();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (int i = 0; i < grantResults.length; i++) {
            permissionsToRequest.add(permissions[i]);
        }
        if (permissionsToRequest.size() > 0) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    private void requestPermissionsIfNecessary(String[] permissions) {
        ArrayList<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                // Permission is not granted
                permissionsToRequest.add(permission);
            }
        }
        if (permissionsToRequest.size() > 0) {
            ActivityCompat.requestPermissions(
                    this,
                    permissionsToRequest.toArray(new String[0]),
                    REQUEST_PERMISSIONS_REQUEST_CODE);
        }
    }

    private List<Polygon> loadPlanungsraumPolygons() {
        List<FeatureInfo> featureList = JsonParser.parseJsonFile(this, "Berlin_LOR_Pl.geojson");

        List<Polygon> planungsraeume = new ArrayList<>();

        for (FeatureInfo feature : featureList) {
            List<?> coordinates = feature.getCoordinates();
            String planungsraum = feature.getPlanungsraum();

            for (Object geometry : coordinates) {
                if (geometry instanceof List) {
                    List<List<Double>> polygon = (List<List<Double>>) geometry;
                    ArrayList<GeoPoint> geoPoints = convertToGeoPoints(polygon);

                    if (!geoPoints.isEmpty() && geoPoints.size() >= 3) {
                        try {
                            Polygon geoPolygon = new Polygon();
                            geoPoints.add(geoPoints.get(0));
                            geoPolygon.setPoints(geoPoints);
                            geoPolygon.getOutlinePaint().setStrokeWidth(2.5f);
                            geoPolygon.getOutlinePaint().setColor(Color.parseColor("#03055B"));
                            geoPolygon.getFillPaint().setColor(Color.parseColor("#26cc0000"));
                            geoPolygon.setTitle(planungsraum);

                            geoPolygon.setOnClickListener(new Polygon.OnClickListener() {
                                @Override
                                public boolean onClick(Polygon polygon, MapView mapView, GeoPoint eventPos) {

                                    if (clickedPolygon != null && originalOutlinePaint != null && originalFillPaint != null) {
                                        clickedPolygon.getOutlinePaint().set(originalOutlinePaint);
                                        clickedPolygon.getFillPaint().set(originalFillPaint);
                                    }

                                    clickedPolygon = polygon;
                                    originalOutlinePaint = new Paint(clickedPolygon.getOutlinePaint());
                                    originalFillPaint = new Paint(clickedPolygon.getFillPaint());

                                    polygon.getOutlinePaint().setStrokeWidth(8.0f);
                                    polygon.getOutlinePaint().setColor(Color.parseColor("#ffd75e"));
                                    polygon.getFillPaint().setColor(Color.parseColor("#266fa8dc"));

                                    loadTreesInPlanungsraum(polygon.getTitle());

                                    map.invalidate();
                                    return true;
                                }
                            });


                            planungsraeume.add(geoPolygon);
                            map.getOverlayManager().add(geoPolygon);
                        } catch (Exception e) {
                            Log.e("Polygon", "Error creating polygon", e);
                        }
                    } else {
                        Log.d("Polygon", "This feature has no valid coordinates!");
                    }
                }
            }
        }

        Log.d("Poly", "Number of polygons added: " + planungsraeume.size());
        listOfPolygons = planungsraeume;

        map.invalidate();

        return planungsraeume;
    }

    private ArrayList<GeoPoint> convertToGeoPoints(List<List<Double>> coordinatesList) {
        ArrayList<GeoPoint> geoPoints = new ArrayList<>();

        for (List<Double> geometryCoordinates : coordinatesList) {
            if (geometryCoordinates.size() >= 2) {
                double longitude = geometryCoordinates.get(0);
                double latitude = geometryCoordinates.get(1);
                geoPoints.add(new GeoPoint(latitude, longitude));
            } else {
                Log.w("GeoPoints", "Invalid coordinate pair found: " + geometryCoordinates.toString());
            }
        }

        return geoPoints;
    }

    private void loadTreesInPlanungsraum(String planungsraum) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);


        String query = "SELECT * FROM baeume WHERE planungsraum = '" + planungsraum + "'";

        boolean isGattungFilterEnabled = sharedPreferences.getBoolean("gattung_filter_enabled", false);
        if (isGattungFilterEnabled) {
            Set<String> selectedGattung = sharedPreferences.getStringSet("gattung", new HashSet<>());
            if (!selectedGattung.isEmpty()) {
                StringBuilder gattungFilterBuilder = new StringBuilder();
                for (String gattung : selectedGattung) {

                    String capitalizedGattung = gattung.toUpperCase();
                    if (gattungFilterBuilder.length() > 0) {
                        gattungFilterBuilder.append("\",\"");
                    }
                    gattungFilterBuilder.append(capitalizedGattung);
                }
                String gattungFilter = gattungFilterBuilder.toString();
                query += " AND gattung_deutsch IN (\"" + gattungFilter + "\")";
            }
        }




        boolean isAlterFilterEnabled = sharedPreferences.getBoolean("alter_filter_enabled", false);
        if (isAlterFilterEnabled) {
            int alterValue = sharedPreferences.getInt("alter", 50);
            query += " AND standalter >= " + alterValue;
        }


        boolean isStammumfangFilterEnabled = sharedPreferences.getBoolean("stammumfang_filter_enabled", false);
        if (isStammumfangFilterEnabled) {
            int stammumfangValue = sharedPreferences.getInt("stammumfg", 300);
            query += " AND stammumfg >= " + stammumfangValue;
        }


        boolean isBaumhoeheFilterEnabled = sharedPreferences.getBoolean("baumhoehe_filter_enabled", false);
        if (isBaumhoeheFilterEnabled) {
            int baumhoeheValue = sharedPreferences.getInt("baumhoehe", 20);
            query += " AND baumhoehe >= " + baumhoeheValue;
        }


        boolean isKronendurchmesserFilterEnabled = sharedPreferences.getBoolean("kronendurchmesser_filter_enabled", false);
        if (isKronendurchmesserFilterEnabled) {
            int kronendurchmesserValue = sharedPreferences.getInt("kronedurch", 10);
            query += " AND kronedurch >= " + kronendurchmesserValue;
        }

        Log.d("QUERY:", query);

        SQLiteDatabase db = dataBaseHelper.getWritableDatabase();
        Cursor cur = db.rawQuery(query, null);

        if (currentMarkerClusterer != null) {
            map.getOverlayManager().remove(currentMarkerClusterer);
        }

        currentMarkerClusterer = new RadiusMarkerClusterer(map.getContext());
        currentMarkerClusterer.setIcon(BonusPackHelper.getBitmapFromVectorDrawable(this, R.drawable.cluster_icon_s)); // Replace with your cluster icon
        currentMarkerClusterer.setRadius(100);
        currentMarkerClusterer.setMaxClusteringZoomLevel(19);

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
                    try {
                        double lon = cur.getDouble(columnIndexX);
                        double lat = cur.getDouble(columnIndexY);
                        String artDtsch = cur.getString(columnIndexArtDtsch);
                        String pflanzjahr = cur.getString(columnIndexPflanzjahr);
                        String standalter = cur.getString(columnIndexStandalter);
                        String artBot = cur.getString(columnIndexArtBot);
                        String stammumfg = cur.getString(columnIndexStammumfg);
                        String baumhoehe = cur.getString(columnIndexBaumhoehe);
                        String kronedurch = cur.getString(columnIndexKronedurch);

                        StringBuilder info = new StringBuilder();
                        info.append("Standalter: " + standalter + " Jahre" + "<br>");
                        info.append("Art (botanisch): " + artBot + "<br>");
                        info.append("Stammumfang: " + stammumfg + " cm" + "<br>");
                        info.append("Baumhöhe: " + baumhoehe + " m" + "<br>");
                        info.append("Kronendurchmesser: " + kronedurch + " m");

                        Marker treeMarker = new Marker(map);
                        treeMarker.setPosition(new GeoPoint(lat, lon));

                        treeMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                        treeMarker.setTitle(artDtsch);
                        treeMarker.setSnippet("Pflanzjahr: " + pflanzjahr);
                        treeMarker.setSubDescription(info.toString());
                        treeMarker.setIcon(ResourcesCompat.getDrawable(getResources(), R.drawable.baum_icon_s, null));

                        currentMarkerClusterer.add(treeMarker);
                    } catch (NumberFormatException e) {
                        Log.e("loadTrees", "Invalid latitude or longitude", e);
                    }
                } else {
                    Log.w("loadTrees", "Invalid column index for x or y");
                }
            } while (cur.moveToNext());
            cur.close();
        } else {
            Toast.makeText(this, "Keine Bäume mit diesen Eigenschaften gefunden", Toast.LENGTH_SHORT).show();
            Log.w("loadTrees", "No trees found for planungsraum: " + planungsraum);
        }

        map.getOverlayManager().add(currentMarkerClusterer);
        map.invalidate();
    }





    @Override
    public void onResume() {
        super.onResume();
        map.onResume(); //needed for compass, my location overlays, v6.0.0 and up
    }

    @Override
    public void onPause() {
        super.onPause();
        map.onPause();  //needed for compass, my location overlays, v6.0.0 and up
    }
}
