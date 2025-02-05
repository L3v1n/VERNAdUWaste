package com.vern.vernaduwaste;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.view.ViewCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NavigationMapActivity extends AppCompatActivity implements MapGridView.MarkerClickListener {

    private static final String TAG = "NavigationMapActivity";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1001;

    private MapGridView mapView;
    private FirebaseHelper firebaseHelper;
    private ProgressBar loadingSpinner;
    private Spinner floorSpinner;
    private ImageButton btnBack;
    private LinearLayout modalBox;
    private Button btnBin;
    private Button btnGoBack;

    private float lastAzimuth = -1;
    private static final float ORIENTATION_THRESHOLD = 5.0f;

    private SensorManager sensorManager;
    private Sensor rotationVectorSensor;
    private boolean isInitialOrientationSet = false;
    private boolean isInitialAltitudeSet = false;
    // Floor maps loaded from assets (JSON files)
    private final Map<Integer, int[][]> floorMaps = new HashMap<>();
    // Instance of AStarPathfinding for computing paths
    private final AStarPathfinding pathfinder = new AStarPathfinding();

    private int[][] mapGrid;
    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                float[] rotationMatrix = new float[9];
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                float[] orientation = new float[3];
                SensorManager.getOrientation(rotationMatrix, orientation);
                float azimuth = Math.round((float) Math.toDegrees(orientation[0]));
                if (azimuth < 0) azimuth += 360;
                if (!isInitialOrientationSet) {
                    lastAzimuth = azimuth;
                    isInitialOrientationSet = true;
                    checkInitialDataLoaded();
                    Log.d(TAG, "Initial orientation set to " + lastAzimuth + "°");
                } else if (Math.abs(azimuth - lastAzimuth) >= ORIENTATION_THRESHOLD) {
                    lastAzimuth = azimuth;
                    Log.d(TAG, "Orientation updated: " + lastAzimuth + "°");
                }
                if (mapView != null) {
                    mapView.setDeviceOrientation(lastAzimuth);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
    private FirebaseHelper.WifiPosition board2;
    private int currentFloor = 1; // default floor
    private int deviceX = -1, deviceY = -1;

    private WifiManager wifiManager;
    private NetworkStateManager networkStateManager;
    private boolean isOfflineMode = false;
    private boolean isFloorInitialized = false;

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private final List<Double> altitudeReadings = new ArrayList<>();
    private static final int ALTITUDE_READINGS_SIZE = 5;
    private int initialFloor = -1;

    private AppState appState;
    // Wi-Fi board positions (waste bin markers) retrieved from Firebase
    private FirebaseHelper.WifiPosition board1;
    // Access point coordinates (for device position calculation)
    private int apX, apY;

    // Selected waste bin (Wi-Fi board) coordinates and floor
    private int selectedBinX = -1, selectedBinY = -1, selectedBinFloor = -1;
    private int lastSelectedBinX = -1, lastSelectedBinY = -1, lastSelectedBinFloor = -1;
    private AlertDialog networkDialog;
    // Device floor (determined from altitude and RSSI)
    private int deviceFloor = -1;
    private boolean isSwitchingFloor = false;
    // Global caches for multi-floor navigation segments:
    private List<int[]> multiFloorPathToStair = null;
    private List<int[]> multiFloorPathFromStair = null;
    private int[] cachedStairCoordinates = null;
    private int cachedGoalFloor = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation_map);

        // Initialize views
        mapView = findViewById(R.id.map_view);
        loadingSpinner = findViewById(R.id.loading_spinner);
        btnBack = findViewById(R.id.btn_back);
        floorSpinner = findViewById(R.id.floor_spinner);
        modalBox = findViewById(R.id.modal_box);
        btnBin = findViewById(R.id.btn_bin);
        btnGoBack = findViewById(R.id.btn_go_back);

        mapView.setMarkerClickListener(this);
        modalBox.setVisibility(View.GONE);

        firebaseHelper = new FirebaseHelper();

        btnBack.setOnClickListener(v -> finish());

        btnBin.setOnClickListener(v -> {
            btnBin.setBackgroundColor(Color.parseColor("#388E3C"));
            openWasteDisposalSuccessActivity();
        });

        btnGoBack.setOnClickListener(v -> {
            modalBox.setVisibility(View.GONE);
            floorSpinner.setVisibility(View.VISIBLE);
            btnBack.setVisibility(View.VISIBLE);
            mapView.deselectMarker();
            mapView.clearPaths();
            selectedBinX = -1;
            selectedBinY = -1;
            selectedBinFloor = -1;
            lastSelectedBinX = -1;
            lastSelectedBinY = -1;
            lastSelectedBinFloor = -1;
            multiFloorPathToStair = null;
            multiFloorPathFromStair = null;
            cachedStairCoordinates = null;
            cachedGoalFloor = -1;
            Log.d(TAG, "Go Back clicked. Navigation paths cleared.");
        });

        appState = AppState.getInstance(this);
        setupFloorSpinner();
        checkLocationPermission();
        initRotationVectorSensor();

        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        networkStateManager = NetworkStateManager.getInstance(this);
        networkStateManager.getWifiConnected().observe(this, isWifiConnected -> handleNetworkState());
        networkStateManager.getInternetAccessible().observe(this, isInternetAccessible -> handleNetworkState());
        handleNetworkState();

        // Retrieve Wi-Fi board positions from Firebase (if needed)
        fetchWifiBoardPositions();

        // Load floor maps from assets (e.g., floor1.json, floor2.json, floor3.json)
        initializeFloors();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handleNetworkState();
    }

    private void showSwitchToWifiDialog() {
        if (networkDialog != null && networkDialog.isShowing()) {
            networkDialog.dismiss();
        }
        networkDialog = new AlertDialog.Builder(this)
                .setTitle("Wi-Fi Connection Required")
                .setMessage("Please switch to a Wi-Fi connection to use this application.")
                .setCancelable(false)
                .setPositiveButton("Retry", (dialog, which) -> handleNetworkState())
                .setNegativeButton("Go Offline Mode", (dialog, which) -> {
                    appState.setOfflineMode(true);
                    isOfflineMode = true;
                    dismissNetworkDialog();
                    mapView.clearPaths();
                    currentFloor = 1;
                    floorSpinner.setSelection(currentFloor - 1);
                    updateFloor();
                    Log.d(TAG, "User chose offline mode.");
                })
                .setNeutralButton("Open Wi-Fi Settings", (dialog, which) -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)))
                .show();
    }

    private void showNoInternetDialog() {
        if (networkDialog != null && networkDialog.isShowing()) {
            networkDialog.dismiss();
        }
        networkDialog = new AlertDialog.Builder(this)
                .setTitle("No Internet Connection")
                .setMessage("Wi‑Fi is connected but no internet access. Go offline?")
                .setCancelable(false)
                .setPositiveButton("Retry", (dialog, which) -> handleNetworkState())
                .setNegativeButton("Go Offline Mode", (dialog, which) -> {
                    appState.setOfflineMode(true);
                    isOfflineMode = true;
                    dismissNetworkDialog();
                    mapView.clearPaths();
                    currentFloor = 1;
                    floorSpinner.setSelection(currentFloor - 1);
                    updateFloor();
                    Log.d(TAG, "User chose offline mode due to no internet.");
                })
                .setNeutralButton("Open Wi-Fi Settings", (dialog, which) -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)))
                .show();
    }

    private void handleNetworkState() {
        if (appState.isOfflineMode()) {
            isOfflineMode = true;
            mapView.clearPaths();
            currentFloor = 1;
            floorSpinner.setSelection(currentFloor - 1);
            updateFloor();
            Log.d(TAG, "Offline mode activated. Ground floor set as default.");
            return;
        }
        boolean isWifiConnected = networkStateManager.isWifiConnected();
        boolean isInternetAccessible = networkStateManager.isInternetAccessible();
        if (isWifiConnected && isInternetAccessible) {
            dismissNetworkDialog();
            isOfflineMode = false;
            updateFloor();
        } else if (!isWifiConnected) {
            showSwitchToWifiDialog();
        } else if (isWifiConnected && !isInternetAccessible) {
            showNoInternetDialog();
        }
    }

    private void setupFloorSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.floor_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        floorSpinner.setAdapter(adapter);
        floorSpinner.setSelection(0);
        floorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isSwitchingFloor) return;
                int selectedFloor = position + 1;
                if (currentFloor != selectedFloor) {
                    isSwitchingFloor = true;
                    currentFloor = selectedFloor;
                    updateFloor();
                    Log.d(TAG, "Floor spinner selected floor: " + currentFloor);
                    new Handler().postDelayed(() -> isSwitchingFloor = false, 500);
                }
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            Log.d(TAG, "Location permission requested.");
        } else {
            initLocationManager();
            Log.d(TAG, "Location permission granted.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE &&
                grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initLocationManager();
            Log.d(TAG, "Location permission granted by user.");
        } else {
            Toast.makeText(this, "Location permission required for navigation.", Toast.LENGTH_LONG).show();
            Log.w(TAG, "Location permission denied.");
        }
    }

    private void dismissNetworkDialog() {
        if (networkDialog != null && networkDialog.isShowing()) {
            networkDialog.dismiss();
            networkDialog = null;
            Log.d(TAG, "Network dialog dismissed.");
        }
    }

    private void showMarkersForCurrentFloor() {
        mapView.clearMarkers();
        if (board1 != null && board1.floor == currentFloor) {
            mapView.addMarkerPosition(board1.x, board1.y);
            Log.d(TAG, "Marker added for Board1 at (" + board1.x + ", " + board1.y + ") on floor " + board1.floor);
        }
        if (board2 != null && board2.floor == currentFloor) {
            mapView.addMarkerPosition(board2.x, board2.y);
            Log.d(TAG, "Marker added for Board2 at (" + board2.x + ", " + board2.y + ") on floor " + board2.floor);
        }
    }

    private void initRotationVectorSensor() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotationVectorSensor != null) {
                sensorManager.registerListener(sensorEventListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
                Log.d(TAG, "Rotation vector sensor initialized.");
            } else {
                Log.w(TAG, "Rotation vector sensor not available.");
            }
        } else {
            Log.e(TAG, "SensorManager not available.");
        }
    }

    private void fetchWifiBoardPositions() {
        firebaseHelper.listenForWifiBoardPositions((board1, board2) -> {
            if (board1 != null && board2 != null) {
                this.board1 = board1;
                this.board2 = board2;
                // (Stairs are now extracted from JSON maps; no need to cache from Firebase.)
                showMarkersForCurrentFloor();
                checkInitialDataLoaded();
                Log.d(TAG, "Wi‑Fi board positions fetched: Board1(" + board1.x + ", " + board1.y +
                        "), Board2(" + board2.x + ", " + board2.y + ")");
            } else {
                showMarkersForCurrentFloor();
                checkInitialDataLoaded();
                Log.w(TAG, "One or both Wi‑Fi board positions are null.");
            }
        });
    }

    private void initLocationManager() {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000)
                .setMinUpdateIntervalMillis(500)
                .setMinUpdateDistanceMeters(0)
                .build();
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                if (locationResult == null) {
                    Log.w(TAG, "Location result null.");
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    if (location != null && location.hasAltitude()) {
                        double newAltitude = location.getAltitude();
                        altitudeReadings.add(newAltitude);
                        if (altitudeReadings.size() > ALTITUDE_READINGS_SIZE) {
                            altitudeReadings.remove(0);
                        }
                        double sum = 0;
                        for (double alt : altitudeReadings) {
                            sum += alt;
                        }
                        double avgAltitude = sum / altitudeReadings.size();
                        int determinedFloor = determineFloorFromAltitude(avgAltitude);
                        if (!isFloorInitialized) {
                            isInitialAltitudeSet = true;
                            isFloorInitialized = true;
                            initialFloor = determinedFloor;
                            currentFloor = determinedFloor;
                            deviceFloor = determinedFloor;
                            runOnUiThread(() -> {
                                floorSpinner.setSelection(currentFloor - 1);
                                updateFloor();
                                Log.d(TAG, "Floor set to " + currentFloor + " based on altitude.");
                            });
                            checkInitialDataLoaded();
                        }
                        int rssi = getCurrentRssi();
                        updateDeviceMarkerPosition(rssi);
                        Log.d(TAG, "Location updated: Altitude=" + newAltitude + ", Floor=" + determinedFloor);
                    }
                }
            }
        };
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
            Log.d(TAG, "Location updates requested.");
        } catch (SecurityException e) {
            Toast.makeText(this, "Location permission not granted.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Location permission error.", e);
        }
    }

    private int getCurrentRssi() {
        if (wifiManager != null) {
            WifiInfo info = wifiManager.getConnectionInfo();
            return info.getRssi();
        }
        return Integer.MIN_VALUE;
    }

    private int determineFloorFromAltitude(double altitude) {
        if (altitude >= 0 && altitude <= 10) {
            return 1;
        } else if (altitude > 10 && altitude <= 20) {
            return 2;
        } else if (altitude > 20) {
            return 3;
        } else {
            return 1;
        }
    }

    private void checkInitialDataLoaded() {
        if (isInitialOrientationSet && isInitialAltitudeSet && floorMaps.size() == 3) {
            new Handler().postDelayed(() -> {
                loadingSpinner.setVisibility(View.GONE);
                mapView.setVisibility(View.VISIBLE);
                mapView.requestLayout();
                mapView.invalidate();
                Log.d(TAG, "Initial data loaded. Map view is now visible.");
            }, 1000);
        }
    }

    private void initializeFloors() {
        loadMapGridFromJson(1, "floor1.json");
        loadMapGridFromJson(2, "floor2.json");
        loadMapGridFromJson(3, "floor3.json");
        Log.d(TAG, "All floor maps loaded.");
    }

    private void loadMapGridFromJson(int floor, String filename) {
        try (InputStream is = getAssets().open(filename)) {
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            String jsonStr = new String(buffer, StandardCharsets.UTF_8);
            JSONArray jsonArray = new JSONArray(jsonStr);
            int[][] grid = new int[jsonArray.length()][jsonArray.getJSONArray(0).length()];
            for (int i = 0; i < jsonArray.length(); i++) {
                JSONArray row = jsonArray.getJSONArray(i);
                for (int j = 0; j < row.length(); j++) {
                    grid[i][j] = row.getInt(j);
                }
            }
            floorMaps.put(floor, grid);
            Log.d(TAG, "Loaded map for floor " + floor);
            if (floor == currentFloor) {
                mapGrid = grid;
                mapView.setMapGrid(mapGrid, deviceFloor);
            }
        } catch (IOException | JSONException e) {
            Toast.makeText(this, "Error loading " + filename, Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error loading floor map: " + filename, e);
        }
    }

    private void updateFloor() {
        if (!floorMaps.containsKey(currentFloor)) {
            Log.w(TAG, "Floor " + currentFloor + " not loaded.");
            return;
        }
        mapGrid = floorMaps.get(currentFloor);
        mapView.setMapGrid(mapGrid, deviceFloor);
        mapView.setCurrentFloor(currentFloor);
        showMarkersForCurrentFloor();
        apX = mapGrid[0].length / 2;
        apY = mapGrid.length / 2;
        if (!isOfflineMode && wifiManager != null && currentFloor == deviceFloor) {
            int rssi = getCurrentRssi();
            updateDeviceMarkerPosition(rssi);
        } else {
            mapView.removeDeviceMarker();
            mapView.clearPaths();
            Log.d(TAG, "Offline mode or deviceFloor mismatch. Device marker removed and paths cleared.");
        }
        // Update navigation path display based on cached multi-floor segments.
        updateNavigationPathDisplay();
        Log.d(TAG, "Floor updated to " + currentFloor);
    }

    private void updateDeviceMarkerPosition(int rssi) {
        if (mapGrid == null) {
            Log.w(TAG, "Map grid is not initialized.");
            return;
        }
        double distance = getDistanceFromRssi(rssi);
        int cellsAway = (int) Math.round(distance / 0.5);
        double angleRad = Math.toRadians(lastAzimuth);
        int deltaX = (int) Math.round(cellsAway * Math.sin(angleRad));
        int deltaY = (int) Math.round(cellsAway * Math.cos(angleRad));
        int newDeviceX = apX + deltaX;
        int newDeviceY = apY - deltaY;
        newDeviceX = Math.max(0, Math.min(newDeviceX, mapGrid[0].length - 1));
        newDeviceY = Math.max(0, Math.min(newDeviceY, mapGrid.length - 1));
        if (pathfinder.isWalkable(mapGrid, newDeviceX, newDeviceY)) {
            deviceX = newDeviceX;
            deviceY = newDeviceY;
            mapView.addDeviceMarkerPosition(deviceX, deviceY);
            Log.d(TAG, "Device marker updated to (" + deviceX + ", " + deviceY + ") on floor " + deviceFloor);
        } else {
            int[] closestWalkable = findClosestWalkablePosition(newDeviceX, newDeviceY);
            if (closestWalkable != null) {
                deviceX = closestWalkable[0];
                deviceY = closestWalkable[1];
                mapView.addDeviceMarkerPosition(deviceX, deviceY);
                Log.d(TAG, "Device marker moved to closest walkable (" + deviceX + ", " + deviceY + ") on floor " + deviceFloor);
            } else {
                Toast.makeText(this, "No walkable path found near device location.", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "No walkable path found near device location.");
            }
        }
    }

    private double getDistanceFromRssi(int rssi) {
        int txPower = -59;
        double n = 2.0;
        return Math.pow(10.0, (txPower - rssi) / (10 * n));
    }

    private int[] findClosestWalkablePosition(int x, int y) {
        int maxDistance = Math.max(mapGrid.length, mapGrid[0].length);
        for (int distance = 1; distance <= maxDistance; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                for (int dy = -distance; dy <= distance; dy++) {
                    int newX = x + dx;
                    int newY = y + dy;
                    if (newX < 0 || newY < 0 || newX >= mapGrid[0].length || newY >= mapGrid.length)
                        continue;
                    if (pathfinder.isWalkable(mapGrid, newX, newY)) {
                        Log.d(TAG, "Closest walkable found at (" + newX + ", " + newY + ") on floor " + deviceFloor);
                        return new int[]{newX, newY};
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void onMarkerClick(int x, int y) {
        if (x == -1 && y == -1) {
            modalBox.setVisibility(View.GONE);
            floorSpinner.setVisibility(View.VISIBLE);
            btnBack.setVisibility(View.VISIBLE);
            mapView.deselectMarker();
            mapView.clearPaths();
            selectedBinX = -1;
            selectedBinY = -1;
            selectedBinFloor = -1;
            lastSelectedBinX = -1;
            lastSelectedBinY = -1;
            lastSelectedBinFloor = -1;
            multiFloorPathToStair = null;
            multiFloorPathFromStair = null;
            cachedStairCoordinates = null;
            cachedGoalFloor = -1;
            Log.d(TAG, "Waste bin deselected. Navigation paths cleared.");
        } else {
            modalBox.setVisibility(View.VISIBLE);
            floorSpinner.setVisibility(View.VISIBLE);
            btnBack.setVisibility(View.GONE);
            ViewCompat.setElevation(floorSpinner, 16f);
            ViewCompat.setElevation(modalBox, 8f);
            if (deviceX >= 0 && deviceY >= 0) {
                int binFloor = currentFloor;
                String binName = null;
                if (board1 != null && x == board1.x && y == board1.y) {
                    binFloor = board1.floor;
                    binName = "Board1";
                } else if (board2 != null && x == board2.x && y == board2.y) {
                    binFloor = board2.floor;
                    binName = "Board2";
                }
                if (binName != null) {
                    selectedBinFloor = binFloor;
                    selectedBinX = x;
                    selectedBinY = y;
                    lastSelectedBinX = selectedBinX;
                    lastSelectedBinY = selectedBinY;
                    lastSelectedBinFloor = selectedBinFloor;
                    // Compute multi-floor path from device location to selected waste bin.
                    handlePathfinding(deviceX, deviceY, selectedBinX, selectedBinY, selectedBinFloor);
                    Log.d(TAG, "Waste bin selected: " + binName + " at (" + x + ", " + y + ") on floor " + binFloor);
                } else {
                    Toast.makeText(this, "Unknown waste bin selected.", Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "Unknown waste bin selected at (" + x + ", " + y + ").");
                }
            } else {
                Toast.makeText(this, "Device location not available.", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Device location unavailable on marker click.");
            }
        }
    }

    // Multi-floor pathfinding: computes paths on the device floor (user-to-stair) and on the goal floor (stair-to-goal)
    // and caches these segments so that when switching floors, the correct navigation line is displayed.
    private void handlePathfinding(int startX, int startY, int goalX, int goalY, int goalFloor) {
        // If invalid start point, do nothing.
        if (startX < 0 || startY < 0) {
            Toast.makeText(this, "User location not available.", Toast.LENGTH_SHORT).show();
            mapView.clearPaths();
            multiFloorPathToStair = null;
            multiFloorPathFromStair = null;
            cachedStairCoordinates = null;
            cachedGoalFloor = -1;
            return;
        }
        mapView.clearPaths();
        Log.d(TAG, "Cleared existing paths before pathfinding.");

        // If same-floor, perform standard A*.
        if (deviceFloor == goalFloor) {
            AStarPathfinding.AStarResult result = pathfinder.aStar(floorMaps.get(goalFloor), startX, startY, goalX, goalY);
            if (result != null && result.path != null) {
                List<int[]> path = new ArrayList<>();
                for (AStarPathfinding.Node node : result.path) {
                    path.add(new int[]{node.x, node.y});
                }
                mapView.setPath(path);
                multiFloorPathToStair = null;
                multiFloorPathFromStair = null;
                cachedStairCoordinates = null;
                cachedGoalFloor = -1;
                Log.d(TAG, "Single-floor path drawn on floor " + goalFloor);
            } else {
                mapView.clearPaths();
                Toast.makeText(this, "No path found on the same floor.", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "No single-floor path found.");
            }
            return;
        }

        // Multi-floor navigation:
        // 1. On the user's (device) floor: extract stairs from JSON map.
        List<int[]> startStairs = pathfinder.getStairsFromGrid(floorMaps.get(deviceFloor));
        int[] nearestStair = pathfinder.findNearestStair(deviceFloor, floorMaps.get(deviceFloor), startX, startY, startStairs);
        if (nearestStair == null) {
            Toast.makeText(this, "No stairs found on your floor.", Toast.LENGTH_SHORT).show();
            mapView.clearPaths();
            multiFloorPathToStair = null;
            multiFloorPathFromStair = null;
            cachedStairCoordinates = null;
            cachedGoalFloor = -1;
            Log.w(TAG, "No stairs found on device floor: " + deviceFloor);
            return;
        }
        AStarPathfinding.AStarResult resultToStair = pathfinder.aStar(floorMaps.get(deviceFloor), startX, startY, nearestStair[0], nearestStair[1]);
        if (resultToStair == null || resultToStair.path == null) {
            Toast.makeText(this, "No path to stairs found on your floor.", Toast.LENGTH_SHORT).show();
            mapView.clearPaths();
            multiFloorPathToStair = null;
            multiFloorPathFromStair = null;
            cachedStairCoordinates = null;
            cachedGoalFloor = -1;
            Log.w(TAG, "No path to stairs found on device floor.");
            return;
        }
        List<int[]> pathToStair = new ArrayList<>();
        for (AStarPathfinding.Node node : resultToStair.path) {
            pathToStair.add(new int[]{node.x, node.y});
        }

        // 2. On the goal floor: extract stairs from JSON map.
        List<int[]> goalStairs = pathfinder.getStairsFromGrid(floorMaps.get(goalFloor));
        boolean stairExists = false;
        for (int[] stair : goalStairs) {
            if (stair[0] == nearestStair[0] && stair[1] == nearestStair[1]) {
                stairExists = true;
                break;
            }
        }
        if (!stairExists) {
            Toast.makeText(this, "Corresponding stair not found on target floor.", Toast.LENGTH_SHORT).show();
            mapView.clearPaths();
            multiFloorPathToStair = null;
            multiFloorPathFromStair = null;
            cachedStairCoordinates = null;
            cachedGoalFloor = -1;
            Log.w(TAG, "Stair (" + nearestStair[0] + "," + nearestStair[1] + ") not found on goal floor " + goalFloor);
            return;
        }
        AStarPathfinding.AStarResult resultFromStair = pathfinder.aStar(floorMaps.get(goalFloor), nearestStair[0], nearestStair[1], goalX, goalY);
        if (resultFromStair == null || resultFromStair.path == null) {
            Toast.makeText(this, "No path from stairs to waste bin found on target floor.", Toast.LENGTH_SHORT).show();
            mapView.clearPaths();
            multiFloorPathToStair = null;
            multiFloorPathFromStair = null;
            cachedStairCoordinates = null;
            cachedGoalFloor = -1;
            Log.w(TAG, "No path from stairs to goal on goal floor.");
            return;
        }
        List<int[]> pathFromStair = new ArrayList<>();
        for (AStarPathfinding.Node node : resultFromStair.path) {
            pathFromStair.add(new int[]{node.x, node.y});
        }

        // Cache the computed segments and stair coordinate.
        multiFloorPathToStair = pathToStair;
        multiFloorPathFromStair = pathFromStair;
        cachedStairCoordinates = nearestStair;
        cachedGoalFloor = goalFloor;

        // Update navigation display based on current floor.
        updateNavigationPathDisplay();
    }

    // Updates the displayed navigation path based on the current floor.
    private void updateNavigationPathDisplay() {
        if (currentFloor == deviceFloor && multiFloorPathToStair != null) {
            mapView.setPath(multiFloorPathToStair);
            Log.d(TAG, "Displaying path from user to stairs on floor " + deviceFloor);
        } else if (currentFloor == cachedGoalFloor && multiFloorPathFromStair != null) {
            mapView.setPath(multiFloorPathFromStair);
            Log.d(TAG, "Displaying path from stairs to waste bin on floor " + cachedGoalFloor);
        } else {
            mapView.clearPaths();
            Log.d(TAG, "Current floor (" + currentFloor + ") not in multi-floor navigation. No path displayed.");
        }
    }

    private void openWasteDisposalSuccessActivity() {
        Intent intent = new Intent(NavigationMapActivity.this, WasteDisposalSuccessActivity.class);
        startActivity(intent);
        Log.d(TAG, "WasteDisposalSuccessActivity launched.");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(sensorEventListener);
            Log.d(TAG, "Sensor listener unregistered.");
        }
        if (fusedLocationClient != null && locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
            Log.d(TAG, "Location updates removed.");
        }
    }
}
