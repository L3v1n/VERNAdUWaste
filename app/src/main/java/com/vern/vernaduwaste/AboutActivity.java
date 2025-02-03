package com.vern.vernaduwaste;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_about);

        // Set up the back button
        findViewById(R.id.back_button).setOnClickListener(v -> onBackPressed());
    }
}
