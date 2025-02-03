package com.vern.vernaduwaste;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class SettingsActivity extends AppCompatActivity {

    private AppState appState;
    private BottomNavigationView bottomNavigationView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        appState = AppState.getInstance(this);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.settings, new SettingsFragment())
                    .commit();
        }

        // Initialize the navbar
        initNavbar();
    }

    private void initNavbar() {
        bottomNavigationView = findViewById(R.id.bottom_navigation);

        // Set the selected item to settings
        bottomNavigationView.setSelectedItemId(R.id.nav_settings);

        bottomNavigationView.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            // Already in SettingsActivity, do nothing
            if (id == R.id.nav_home) {
                // Navigate back to MainActivity
                Intent intent = new Intent(SettingsActivity.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish();
                return true;
            } else return id == R.id.nav_settings;
        });
    }

    public static class SettingsFragment extends PreferenceFragmentCompat {

        private SwitchPreferenceCompat offlineModePreference;
        private AppState appState;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            // Load the preferences from the XML resource
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            // Initialize AppState with context
            appState = AppState.getInstance(requireContext());

            // Find the preference by key
            offlineModePreference = findPreference("offline_mode");

            if (offlineModePreference != null) {
                // Set initial state based on AppState
                offlineModePreference.setChecked(appState.isOfflineMode());

                // Update summary
                updateOfflineModeSummary(offlineModePreference.isChecked());

                // Set listener
                offlineModePreference.setOnPreferenceChangeListener((preference, newValue) -> {
                    boolean isOffline = (Boolean) newValue;
                    appState.setOfflineMode(isOffline);
                    updateOfflineModeSummary(isOffline);

                    String message = isOffline ? "App is now in offline mode" : "App is now online";
                    Toast.makeText(getActivity(), message, Toast.LENGTH_SHORT).show();

                    return true;
                });
            } else {
                // Log for debugging
                Toast.makeText(getActivity(), "Error: offlineModePreference is null", Toast.LENGTH_SHORT).show();
            }

            // About button functionality
            findPreference("about").setOnPreferenceClickListener(preference -> {
                Intent intent = new Intent(getActivity(), AboutActivity.class);
                startActivity(intent);
                return true;
            });
        }

        private void updateOfflineModeSummary(boolean isOffline) {
            if (offlineModePreference != null) {
                offlineModePreference.setSummary(isOffline ? "App is in offline mode" : "App is online");
            }
        }
    }
}
