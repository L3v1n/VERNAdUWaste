package com.vern.vernaduwaste;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class WasteDisposalSuccessActivity extends AppCompatActivity {

    private Button btnReturnHome;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waste_disposal_success);

        btnReturnHome = findViewById(R.id.btn_return_home);

        btnReturnHome.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                DatabaseHelper dbHelper = new DatabaseHelper(WasteDisposalSuccessActivity.this);
                dbHelper.incrementWasteCount();

                Intent intent = new Intent(WasteDisposalSuccessActivity.this, MainActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                finish(); // Finish current Activity
            }
        });
    }

    @Override
    public void finish() {
        super.finish();
        // Optionally, add transition animations if desired
        // overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
    }
}
