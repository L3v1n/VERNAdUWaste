package com.vern.vernaduwaste;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

public class SSActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ss);

        // Show the splash screen for 1 second before navigating to MainActivity
        new Handler().postDelayed(() -> {
            startActivity(new Intent(SSActivity.this, MainActivity.class));
            finish();
        }, 1000);
    }
}