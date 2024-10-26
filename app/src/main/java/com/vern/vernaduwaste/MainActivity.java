package com.vern.vernaduwaste;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        FloatingActionButton fabAdd = findViewById(R.id.fab_add);
        fabAdd.setOnClickListener(v -> openCameraActivity());
    }

    // Opens CameraActivity for waste image capture
    private void openCameraActivity() {
        startActivity(new Intent(MainActivity.this, CameraActivity.class));
    }
}