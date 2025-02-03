package com.vern.vernaduwaste;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

import androidx.appcompat.app.AppCompatActivity;

public class SSActivity extends AppCompatActivity {
    private static final String TAG = "SSActivity";

    private NetworkStateManager networkStateManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ss);

        // Initialize the NetworkStateManager singleton
        networkStateManager = NetworkStateManager.getInstance(this);

        // Proceed to MainActivity after a delay
        new Handler().postDelayed(() -> {
            Intent intent = new Intent(SSActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }, 1000); // Delay for splash screen duration
    }
}
