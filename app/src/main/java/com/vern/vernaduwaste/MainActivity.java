package com.vern.vernaduwaste;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.database.FirebaseDatabase;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private FirebaseHelper firebaseHelper;
    private AlertDialog networkDialog;

    private NetworkStateManager networkStateManager;
    private AppState appState;

    private BottomNavigationView bottomNavigationView;
    private boolean wasOffline = false; // To track offline to online transition

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main); // Ensure your main layout includes the navbar

        try {
            FirebaseDatabase.getInstance().setPersistenceEnabled(true);
        } catch (Exception e) {
            // Ignore if persistence is already enabled
        }

        appState = AppState.getInstance(this);

        networkStateManager = NetworkStateManager.getInstance(this);

        // Observe network changes
        networkStateManager.getWifiConnected().observe(this, isWifiConnected -> {
            handleNetworkState();
        });

        networkStateManager.getInternetAccessible().observe(this, isInternetAccessible -> {
            handleNetworkState();
        });

        // Call handleNetworkState() explicitly to handle the initial state
        handleNetworkState();

        findViewById(R.id.fab_add).setOnClickListener(v -> openCameraActivity());

        // Initialize the navbar
        initNavbar();
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Set the selected item in the BottomNavigationView to nav_home
        if (bottomNavigationView != null) {
            bottomNavigationView.setSelectedItemId(R.id.nav_home);
        }

        // Retrieve waste count from the database
        DatabaseHelper dbHelper = new DatabaseHelper(this);
        int wasteCount = dbHelper.getWasteCount();

        // Update the TextView
        TextView tvWasteCount = findViewById(R.id.tv_waste_count);
        tvWasteCount.setText(String.valueOf(wasteCount));
    }

    private void openCameraActivity() {
        Intent intent = new Intent(MainActivity.this, CameraActivity.class);
        startActivity(intent);
    }

    private void handleNetworkState() {
        if (appState.isOfflineMode()) {
            // Offline mode is active
            if (!wasOffline) {
                Toast.makeText(this, "App is in offline mode", Toast.LENGTH_SHORT).show();
                wasOffline = true;
            }
            // Proceed without checking network state
            return;
        }

        boolean isWifiConnected = networkStateManager.isWifiConnected();
        boolean isInternetAccessible = networkStateManager.isInternetAccessible();

        if (isWifiConnected && isInternetAccessible) {
            dismissNetworkDialog();
            if (firebaseHelper == null) {
                firebaseHelper = new FirebaseHelper();
            }
            if (wasOffline) {
                Toast.makeText(this, "App is back online", Toast.LENGTH_SHORT).show();
                wasOffline = false;
            }
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
                .setPositiveButton("Retry", (dialog, which) -> {
                    handleNetworkState(); // Re-check the network state
                })
                .setNegativeButton("Go Offline Mode", (dialog, which) -> {
                    appState.setOfflineMode(true);
                    dismissNetworkDialog();
                    Toast.makeText(this, "App is in offline mode", Toast.LENGTH_SHORT).show();
                    wasOffline = true;
                })
                .setNeutralButton("Open Wi-Fi Settings", (dialog, which) -> {
                    startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
                })
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
                .setPositiveButton("Retry", (dialog, which) -> {
                    handleNetworkState(); // Re-check the network state
                })
                .setNegativeButton("Go Offline Mode", (dialog, which) -> {
                    appState.setOfflineMode(true);
                    dismissNetworkDialog();
                    Toast.makeText(this, "App is in offline mode", Toast.LENGTH_SHORT).show();
                    wasOffline = true;
                })
                .setNeutralButton("Open Wi-Fi Settings", (dialog, which) -> {
                    startActivity(new Intent(android.provider.Settings.ACTION_WIFI_SETTINGS));
                })
                .show();
    }

    private void dismissNetworkDialog() {
        if (networkDialog != null && networkDialog.isShowing()) {
            networkDialog.dismiss();
            networkDialog = null;
        }
    }

    private void initNavbar() {
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // Set the initial selected item to nav_home
        bottomNavigationView.setSelectedItemId(R.id.nav_home);

        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_home) {
                // Show the main layout or refresh if needed
                return true;
            } else if (id == R.id.nav_settings) {
                // Launch SettingsActivity
                Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                startActivity(intent);
                return true;
            } else {
                return false;
            }
        });
    }
}
