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
    private int currentFloor = 1; // Default to ground floor
    private boolean isFloorInitialized = false; // New flag to prevent automatic switching

    private int[][] mapGrid;

    private FirebaseHelper.WifiPosition board1;
    private FirebaseHelper.WifiPosition board2;

    private int apX, apY;  // Access point coordinates (center of the map)
    private int deviceX = -1, deviceY = -1;

    private WifiManager wifiManager;

    private NetworkStateManager networkStateManager;
    private boolean isOfflineMode = false;

    private AlertDialog networkDialog; // For displaying network-related dialogs

    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;

    private final List<Double> altitudeReadings = new ArrayList<>();
    private static final int ALTITUDE_READINGS_SIZE = 5;
    private int initialFloor = -1;

    private AppState appState;

    private final Map<Integer, int[][]> floorMaps = new HashMap<>();
    private final Map<Integer, List<int[]>> stairsMap = new HashMap<>(); // Map of floor to stairs

    private final AStarPathfinding pathfinder = new AStarPathfinding();
    private int selectedBinX = -1, selectedBinY = -1;
    private int selectedBinFloor = -1;

    private int lastSelectedBinX = -1, lastSelectedBinY = -1;
    private int lastSelectedBinFloor = -1;

    // New variable to store device's floor
    private int deviceFloor = -1;

    // Debounce flag for floor switching
    private boolean isSwitchingFloor = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation_map);

        // Initialize Views
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
            openWasteDisposalSuccessActivity(); // Launch success Activity
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

            stairsMap.clear(); // Ensure stairsMap are cleared

            Log.d(TAG, "Go Back button clicked. Navigation paths cleared.");
        });

        appState = AppState.getInstance(this);

        setupFloorSpinner();
        checkLocationPermission();
        initRotationVectorSensor();

        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);

        networkStateManager = NetworkStateManager.getInstance(this);

        networkStateManager.getWifiConnected().observe(this, isWifiConnected -> {
            handleNetworkState();
        });

        networkStateManager.getInternetAccessible().observe(this, isInternetAccessible -> {
            handleNetworkState();
        });

        handleNetworkState();

        fetchWifiBoardPositions();
        initializeFloors();
    }

    @Override
    protected void onResume() {
        super.onResume();
        handleNetworkState();
    }

    private void handleNetworkState() {
        if (appState.isOfflineMode()) {
            isOfflineMode = true;
            mapView.clearPaths();
            stairsMap.clear();
            // Set ground floor as default when offline
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
                    stairsMap.clear();
                    // Set ground floor as default when going offline
                    currentFloor = 1;
                    floorSpinner.setSelection(currentFloor - 1);
                    updateFloor();
                    Log.d(TAG, "User chose to go offline mode.");
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
                .setMessage("You are connected to Wi-Fi but there's no internet access. Would you like to go offline?")
                .setCancelable(false)
                .setPositiveButton("Retry", (dialog, which) -> handleNetworkState())
                .setNegativeButton("Go Offline Mode", (dialog, which) -> {
                    appState.setOfflineMode(true);
                    isOfflineMode = true;
                    dismissNetworkDialog();
                    mapView.clearPaths();
                    stairsMap.clear();
                    // Set ground floor as default when going offline
                    currentFloor = 1;
                    floorSpinner.setSelection(currentFloor - 1);
                    updateFloor();
                    Log.d(TAG, "User chose to go offline mode due to no internet.");
                })
                .setNeutralButton("Open Wi-Fi Settings", (dialog, which) -> startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS)))
                .show();
    }

    private void dismissNetworkDialog() {
        if (networkDialog != null && networkDialog.isShowing()) {
            networkDialog.dismiss();
            networkDialog = null;
            Log.d(TAG, "Network dialog dismissed.");
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
                if (isSwitchingFloor) {
                    return;
                }
                int selectedFloor = position + 1;
                if (currentFloor != selectedFloor) {
                    isSwitchingFloor = true;
                    currentFloor = selectedFloor;
                    updateFloor();
                    Log.d(TAG, "Floor spinner selected floor: " + currentFloor);
                    // Allow some time before allowing another switch
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
            Log.d(TAG, "Location permission already granted.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initLocationManager();
                Log.d(TAG, "Location permission granted by user.");
            } else {
                Toast.makeText(this, "Location permission is required for navigation.", Toast.LENGTH_LONG).show();
                Log.w(TAG, "Location permission denied by user.");
            }
        }
    }

    private void fetchWifiBoardPositions() {
        firebaseHelper.listenForWifiBoardPositions((board1, board2) -> {
            if (board1 != null && board2 != null) {
                this.board1 = board1;
                this.board2 = board2;

                addStairsToStairsMap(board1);
                addStairsToStairsMap(board2);

                showMarkersForCurrentFloor();
                checkInitialDataLoaded();
                Log.d(TAG, "Wi-Fi board positions fetched and markers updated.");
            } else {
                showMarkersForCurrentFloor();
                checkInitialDataLoaded();
                Log.w(TAG, "Wi-Fi board positions are null.");
            }
        });
    }

    private void addStairsToStairsMap(FirebaseHelper.WifiPosition board) {
        if (stairsMap.containsKey(board.floor)) {
            stairsMap.get(board.floor).add(new int[]{board.x, board.y});
        } else {
            List<int[]> stairs = new ArrayList<>();
            stairs.add(new int[]{board.x, board.y});
            stairsMap.put(board.floor, stairs);
        }
    }

    private void showMarkersForCurrentFloor() {
        mapView.clearMarkers();
        if (board1 != null && board1.floor == currentFloor) {
            mapView.addMarkerPosition(board1.x, board1.y);
            Log.d(TAG, "Marker added for board1 at (" + board1.x + ", " + board1.y + ") on floor " + board1.floor);
        }
        if (board2 != null && board2.floor == currentFloor) {
            mapView.addMarkerPosition(board2.x, board2.y);
            Log.d(TAG, "Marker added for board2 at (" + board2.x + ", " + board2.y + ") on floor " + board2.floor);
        }
    }

    private void initRotationVectorSensor() {
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            rotationVectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
            if (rotationVectorSensor != null) {
                sensorManager.registerListener(sensorEventListener, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI);
                Log.d(TAG, "Rotation vector sensor initialized and listener registered.");
            } else {
                Log.w(TAG, "Rotation vector sensor not available.");
            }
        } else {
            Log.e(TAG, "SensorManager not available.");
        }
    }

    private final SensorEventListener sensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
                float[] rotationMatrix = new float[9];
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

                float[] orientation = new float[3];
                SensorManager.getOrientation(rotationMatrix, orientation);
                float azimuthInDegrees = Math.round((float) Math.toDegrees(orientation[0]));
                if (azimuthInDegrees < 0) azimuthInDegrees += 360;

                if (!isInitialOrientationSet) {
                    lastAzimuth = azimuthInDegrees;
                    isInitialOrientationSet = true;
                    checkInitialDataLoaded();
                    Log.d(TAG, "Initial device orientation set to " + lastAzimuth + " degrees.");
                } else if (Math.abs(azimuthInDegrees - lastAzimuth) >= ORIENTATION_THRESHOLD) {
                    lastAzimuth = azimuthInDegrees;
                    Log.d(TAG, "Orientation updated: " + lastAzimuth + " degrees");
                }

                if (mapView != null) {
                    mapView.setDeviceOrientation(lastAzimuth);
                }
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    };

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
                    Log.w(TAG, "Location result is null.");
                    return;
                }
                for (Location location : locationResult.getLocations()) {
                    if (location != null && location.hasAltitude()) {
                        double newAltitude = location.getAltitude(); // Changed to double
                        altitudeReadings.add(newAltitude); // Now adding a double
                        if (altitudeReadings.size() > ALTITUDE_READINGS_SIZE) {
                            altitudeReadings.remove(0);
                        }
                        double sumAltitude = 0;
                        for (double alt : altitudeReadings) {
                            sumAltitude += alt;
                        }
                        double averageAltitude = sumAltitude / altitudeReadings.size();
                        int determinedFloor = determineFloorFromAltitude(averageAltitude);

                        if (!isFloorInitialized) { // Set floor only once
                            isInitialAltitudeSet = true;
                            isFloorInitialized = true;
                            initialFloor = determinedFloor;
                            currentFloor = determinedFloor;
                            deviceFloor = determinedFloor; // Initialize deviceFloor
                            runOnUiThread(() -> {
                                floorSpinner.setSelection(currentFloor - 1);
                                updateFloor();
                                Log.d(TAG, "Floor spinner set to floor " + currentFloor + " based on altitude.");
                            });
                            checkInitialDataLoaded();
                        }

                        // Update device marker position based on RSSI and orientation
                        int rssi = getCurrentRssi();
                        updateDeviceMarkerPosition(rssi);

                        Log.d(TAG, "Location updated: Altitude = " + newAltitude + ", Floor = " + determinedFloor);
                    }
                }
            }
        };

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null);
            Log.d(TAG, "Location updates requested.");
        } catch (SecurityException e) {
            Toast.makeText(this, "Location permission not granted.", Toast.LENGTH_LONG).show();
            Log.e(TAG, "Location permission not granted.", e);
        }
    }

    private int getCurrentRssi() {
        if (wifiManager != null) {
            WifiInfo wifiInfo = wifiManager.getConnectionInfo();
            return wifiInfo.getRssi();
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
                // Removed initiateInitialNavigation() to prevent automatic path drawing
                Log.d(TAG, "Initial data loaded. No automatic navigation path set.");
            }, 1000);
        }
    }

    private void initiateInitialNavigation() {
        // This method has been disabled to prevent automatic path drawing on startup
        Log.d(TAG, "Initial navigation path not initiated.");
    }

    private int[] findNearestWasteBin(int deviceX, int deviceY, int deviceFloor) {
        List<int[]> binPositions = new ArrayList<>();
        if (board1 != null && board1.floor == deviceFloor) {
            binPositions.add(new int[]{board1.x, board1.y});
        }
        if (board2 != null && board2.floor == deviceFloor) {
            binPositions.add(new int[]{board2.x, board2.y});
        }

        if (binPositions.isEmpty()) return null;

        int minDistance = Integer.MAX_VALUE;
        int[] nearestBin = null;
        for (int[] bin : binPositions) {
            int distance = Math.abs(deviceX - bin[0]) + Math.abs(deviceY - bin[1]);
            if (distance < minDistance) {
                minDistance = distance;
                nearestBin = bin;
            }
        }
        return nearestBin;
    }

    @Override
    public void onMarkerClick(int x, int y) {
        if (x == -1 && y == -1) {
            // Deselection logic
            modalBox.setVisibility(View.GONE);
            floorSpinner.setVisibility(View.VISIBLE);
            btnBack.setVisibility(View.VISIBLE);
            mapView.deselectMarker();
            mapView.clearPaths(); // Clear navigation paths
            selectedBinX = -1;
            selectedBinY = -1;
            selectedBinFloor = -1;

            lastSelectedBinX = -1;
            lastSelectedBinY = -1;
            lastSelectedBinFloor = -1;

            stairsMap.clear(); // Ensure stairsMap are cleared

            Log.d(TAG, "Waste bin deselected. Navigation paths cleared.");
        } else {
            // Selection logic
            modalBox.setVisibility(View.VISIBLE);
            floorSpinner.setVisibility(View.VISIBLE); // Ensure floorSpinner remains visible
            btnBack.setVisibility(View.GONE);

            // Adjust elevation to ensure floor_spinner is above modal_box
            ViewCompat.setElevation(floorSpinner, 16f); // Higher elevation
            ViewCompat.setElevation(modalBox, 8f); // Lower elevation

            if (deviceX >= 0 && deviceY >= 0) {
                int binFloor = currentFloor;
                String selectedBinName = null;

                if (board1 != null && x == board1.x && y == board1.y) {
                    binFloor = board1.floor;
                    selectedBinName = "Board1";
                } else if (board2 != null && x == board2.x && y == board2.y) {
                    binFloor = board2.floor;
                    selectedBinName = "Board2";
                }

                if (selectedBinName != null) {
                    selectedBinFloor = binFloor;
                    selectedBinX = x;
                    selectedBinY = y;
                    lastSelectedBinX = selectedBinX;
                    lastSelectedBinY = selectedBinY;
                    lastSelectedBinFloor = selectedBinFloor;
                    performPathfinding(deviceX, deviceY, selectedBinX, selectedBinY, selectedBinFloor);

                    Log.d(TAG, "Waste bin selected: " + selectedBinName + " at (" + x + ", " + y + ") on floor " + binFloor);
                } else {
                    Toast.makeText(this, "Unknown waste bin selected.", Toast.LENGTH_SHORT).show();
                    Log.w(TAG, "Unknown waste bin selected at (" + x + ", " + y + ").");
                }
            } else {
                Toast.makeText(this, "Device location not available.", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "Device location not available when marker clicked.");
            }
        }
    }

    /**
     * Helper method to check if a bin is currently selected.
     *
     * @return True if a bin is selected; False otherwise.
     */
    private boolean isBinSelected() {
        return selectedBinX != -1 && selectedBinY != -1 && selectedBinFloor != -1;
    }

    /**
     * Performs pathfinding only when a bin is selected.
     */
    private void performPathfinding(int startX, int startY, int goalX, int goalY, int goalFloor) {
        if (isOfflineMode) {
            Toast.makeText(this, "Offline mode: Navigation paths are unavailable.", Toast.LENGTH_SHORT).show();
            mapView.clearPaths();
            stairsMap.clear();
            Log.w(TAG, "Attempted pathfinding in offline mode.");
            return;
        }

        // Clear existing paths to prevent overlapping
        mapView.clearPaths();
        stairsMap.clear();
        Log.d(TAG, "Existing paths cleared before performing new pathfinding.");

        // Utilize the multi-floor pathfinding
        List<AStarPathfinding.Node> pathNodes = pathfinder.findPathAcrossFloors(
                floorMaps,
                currentFloor, startX, startY,
                goalFloor, goalX, goalY,
                stairsMap
        );

        if (pathNodes != null) {
            List<int[]> path = new ArrayList<>();
            for (AStarPathfinding.Node node : pathNodes) {
                path.add(new int[]{node.x, node.y});
            }

            // Assign the new path
            mapView.setPath(path);
            Log.d(TAG, "Path found from (" + startX + ", " + startY + ", Floor " + currentFloor + ") to (" + goalX + ", " + goalY + ", Floor " + goalFloor + ")");
        } else {
            mapView.clearPaths();
            Toast.makeText(this, "No path found to the selected waste bin.", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "No path found from (" + startX + ", " + startY + ", Floor " + currentFloor + ") to (" + goalX + ", " + goalY + ", Floor " + goalFloor + ")");
        }
    }

    private void updateFloor() {
        if (!floorMaps.containsKey(currentFloor)) {
            Log.w(TAG, "Floor " + currentFloor + " map not loaded.");
            return;
        }

        mapGrid = floorMaps.get(currentFloor);
        mapView.setMapGrid(mapGrid, deviceFloor);
        mapView.setCurrentFloor(currentFloor); // Inform MapGridView of the current floor

        showMarkersForCurrentFloor();

        apX = mapGrid[0].length / 2;
        apY = mapGrid.length / 2;

        // Update device marker only if currentFloor matches deviceFloor and not in offline mode
        if (!isOfflineMode && wifiManager != null && currentFloor == deviceFloor) {
            int rssi = getCurrentRssi();
            updateDeviceMarkerPosition(rssi);
        } else {
            mapView.removeDeviceMarker();
            // Clear paths as device marker is not visible
            mapView.clearPaths();
            stairsMap.clear();
            Log.d(TAG, "Offline mode active or deviceFloor mismatch. Device marker removed and paths cleared.");
        }

        // **Only set paths if a bin is currently selected and paths exist for this floor**
        if (isBinSelected()) {
            // Paths are managed by the multi-floor pathfinding
            Log.d(TAG, "Bin is selected. Path will be managed by pathfinding.");
        } else {
            mapView.clearPaths();
            Log.d(TAG, "No bin selected. Paths cleared.");
        }

        Log.d(TAG, "Floor updated to " + currentFloor);
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
            Toast.makeText(this, "Error loading floor map: " + filename, Toast.LENGTH_LONG).show();
            Log.e(TAG, "Error loading floor map: " + filename, e);
        }
    }

    private void initializeFloors() {
        loadMapGridFromJson(1, "floor1.json");
        loadMapGridFromJson(2, "floor2.json");
        loadMapGridFromJson(3, "floor3.json");
        Log.d(TAG, "All floor maps initialized.");
    }

    private boolean isWalkable(int x, int y) {
        return mapGrid != null && x >= 0 && y >= 0 && y < mapGrid.length && x < mapGrid[0].length && (mapGrid[y][x] == 0 || mapGrid[y][x] == 2);
    }

    private void updateDeviceMarkerPosition(int rssi) {
        if (mapGrid == null) {
            Log.w(TAG, "Map grid is not initialized.");
            return;
        }

        // Improved distance calculation based on RSSI
        double distance = getDistanceFromRssi(rssi);
        int cellsAway = (int) Math.round(distance / 0.5); // Adjust scaling factor as needed

        // Calculate new position based on orientation
        double angleRad = Math.toRadians(lastAzimuth);
        int deltaX = (int) Math.round(cellsAway * Math.sin(angleRad));
        int deltaY = (int) Math.round(cellsAway * Math.cos(angleRad));

        int newDeviceX = apX + deltaX;
        int newDeviceY = apY - deltaY;

        // Clamp to map boundaries
        newDeviceX = Math.max(0, Math.min(newDeviceX, mapGrid[0].length - 1));
        newDeviceY = Math.max(0, Math.min(newDeviceY, mapGrid.length - 1));

        if (isWalkable(newDeviceX, newDeviceY)) {
            deviceX = newDeviceX;
            deviceY = newDeviceY;
            mapView.addDeviceMarkerPosition(deviceX, deviceY);
            Log.d(TAG, "Device marker updated to (" + deviceX + ", " + deviceY + ") on floor " + deviceFloor);
            // Removed initiateInitialNavigation(); to prevent automatic path drawing
        } else {
            int[] closestWalkable = findClosestWalkablePosition(newDeviceX, newDeviceY);
            if (closestWalkable != null) {
                deviceX = closestWalkable[0];
                deviceY = closestWalkable[1];
                mapView.addDeviceMarkerPosition(deviceX, deviceY);
                Log.d(TAG, "Device marker moved to closest walkable position (" + deviceX + ", " + deviceY + ") on floor " + deviceFloor);
                // Removed initiateInitialNavigation(); to prevent automatic path drawing
            } else {
                Toast.makeText(this, "No walkable path found near device location.", Toast.LENGTH_SHORT).show();
                Log.w(TAG, "No walkable path found near device location.");
            }
        }
    }

    private double getDistanceFromRssi(int rssi) {
        int txPower = -59; // Typical RSSI value at 1 meter
        double n = 2.0; // Path-loss exponent
        return Math.pow(10.0, (txPower - rssi) / (10 * n));
    }

    private int[] findClosestWalkablePosition(int x, int y) {
        int maxDistance = Math.max(mapGrid.length, mapGrid[0].length);

        for (int distance = 1; distance <= maxDistance; distance++) {
            for (int dx = -distance; dx <= distance; dx++) {
                for (int dy = -distance; dy <= distance; dy++) {
                    int newX = x + dx;
                    int newY = y + dy;
                    if (newX < 0 || newY < 0 || newX >= mapGrid[0].length || newY >= mapGrid.length) {
                        continue;
                    }
                    if (isWalkable(newX, newY)) {
                        Log.d(TAG, "Closest walkable position found at (" + newX + ", " + newY + ") on floor " + deviceFloor);
                        return new int[]{newX, newY};
                    }
                }
            }
        }
        return null;
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

    private void findAndSetDevicePosition() {
        if (mapGrid == null) return;

        deviceX = mapGrid[0].length / 2;
        deviceY = mapGrid.length / 2;
        mapView.addDeviceMarkerPosition(deviceX, deviceY);
        Log.d(TAG, "Device marker initially set at center (" + deviceX + ", " + deviceY + ").");
    }

    /**
     * Opens the WasteDisposalSuccessActivity indicating successful disposal.
     */
    private void openWasteDisposalSuccessActivity() {
        Intent intent = new Intent(NavigationMapActivity.this, WasteDisposalSuccessActivity.class);
        startActivity(intent);
        // Optionally, you can finish the current Activity if you don't want users to return to it
        // finish();
        Log.d(TAG, "WasteDisposalSuccessActivity launched.");
    }

    /**
     * Handles pathfinding using the multi-floor A* algorithm.
     *
     * @param startX     Starting X coordinate.
     * @param startY     Starting Y coordinate.
     * @param goalX      Goal X coordinate.
     * @param goalY      Goal Y coordinate.
     * @param goalFloor  Goal floor number.
     */
    private void handlePathfinding(int startX, int startY, int goalX, int goalY, int goalFloor) {
        if (isOfflineMode) {
            Toast.makeText(this, "Offline mode: Navigation paths are unavailable.", Toast.LENGTH_SHORT).show();
            mapView.clearPaths();
            Log.w(TAG, "Attempted pathfinding in offline mode.");
            return;
        }

        // Clear existing paths to prevent overlapping
        mapView.clearPaths();
        stairsMap.clear();
        Log.d(TAG, "Existing paths cleared before performing new pathfinding.");

        // Utilize the multi-floor pathfinding
        List<AStarPathfinding.Node> pathNodes = pathfinder.findPathAcrossFloors(
                floorMaps,
                currentFloor, startX, startY,
                goalFloor, goalX, goalY,
                stairsMap
        );

        if (pathNodes != null) {
            List<int[]> path = new ArrayList<>();
            for (AStarPathfinding.Node node : pathNodes) {
                path.add(new int[]{node.x, node.y});
            }

            // Assign the new path
            mapView.setPath(path);
            Log.d(TAG, "Path found from (" + startX + ", " + startY + ", Floor " + currentFloor + ") to (" + goalX + ", " + goalY + ", Floor " + goalFloor + ")");
        } else {
            mapView.clearPaths();
            Toast.makeText(this, "No path found to the selected waste bin.", Toast.LENGTH_SHORT).show();
            Log.w(TAG, "No path found from (" + startX + ", " + startY + ", Floor " + currentFloor + ") to (" + goalX + ", " + goalY + ", Floor " + goalFloor + ")");
        }
    }
}
