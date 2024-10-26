package com.vern.vernaduwaste;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.Spinner;

import androidx.appcompat.app.AppCompatActivity;

import com.github.chrisbanes.photoview.PhotoView;

public class NavigationMapActivity extends AppCompatActivity {

    private PhotoView mapView;
    private boolean isAnimatingZoom = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_navigation_map);

        mapView = findViewById(R.id.map_view);
        ImageButton btnBack = findViewById(R.id.btn_back);
        Spinner floorSpinner = findViewById(R.id.floor_spinner);

        // Set up the back button to finish the activity when clicked
        btnBack.setOnClickListener(v -> finish());

        // Set up the floor selection spinner with appropriate floor images
        setupFloorSpinner(floorSpinner);

        // Load the initial map image using XML drawable
        loadMapImage(R.drawable.map_sv_gf);

        // Set up a listener to handle the layout and set zoom levels smoothly
        setupZoomBehavior();
    }

    /**
     * Sets up the floor selection spinner to change maps based on user selection.
     */
    private void setupFloorSpinner(Spinner spinner) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.floor_options, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Set up a listener for floor selection changes
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (isAnimatingZoom) return;

                // Smoothly zoom out before switching floors
                smoothZoomTo(1.0f);
                mapView.postDelayed(() -> {
                    // Load the selected floor map
                    switch (position) {
                        case 0:
                            loadMapImage(R.drawable.map_sv_gf);
                            break;
                        case 1:
                            loadMapImage(R.drawable.map_sv_2f);
                            break;
                        case 2:
                            loadMapImage(R.drawable.map_sv_3f);
                            break;
                    }

                    // Smoothly zoom in to the desired scale after the floor map loads
                    mapView.postDelayed(() -> smoothZoomTo(3f), 300);
                }, 300); // Delay to allow the zoom-out animation to complete
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Default to ground floor map if nothing is selected
                if (isAnimatingZoom) return;
                smoothZoomTo(1.0f);
                mapView.postDelayed(() -> loadMapImage(R.drawable.map_sv_gf), 300);
            }
        });
    }

    /**
     * Loads the map image based on the selected floor.
     *
     * @param drawableId The resource ID of the drawable to be loaded.
     */
    private void loadMapImage(int drawableId) {
        mapView.setImageResource(drawableId);
    }

    /**
     * Smoothly animates zoom to a specific scale.
     *
     * @param scale The target scale for zoom.
     */
    private void smoothZoomTo(float scale) {
        if (isAnimatingZoom) return;
        isAnimatingZoom = true;
        mapView.setScale(scale, true);
        mapView.postDelayed(() -> isAnimatingZoom = false, 500); // Adjust this duration to match the zoom animation time
    }

    /**
     * Sets up the zoom behavior when the view layout is completed.
     */
    private void setupZoomBehavior() {
        mapView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            mapView.setMaximumScale(6.0f);
            mapView.setMediumScale(3.0f);
            mapView.setMinimumScale(1.0f);

            // Smoothly animate to the initial zoom level
            mapView.postDelayed(() -> smoothZoomTo(3f), 100);
        });
    }
}
