package com.vern.vernaduwaste;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;

public class SSActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ss);

        // Redirect to MainActivity after a delay
        new Handler().postDelayed(() -> {
            startActivity(new Intent(SSActivity.this, MainActivity.class));
            finish();
        }, 2000);
    }
}
