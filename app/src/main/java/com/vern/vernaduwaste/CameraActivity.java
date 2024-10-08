package com.vern.vernaduwaste;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.*;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.*;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private Camera camera;
    private boolean isFlashOn = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Initialize UI elements
        previewView = findViewById(R.id.previewView);
        FloatingActionButton fabCapture = findViewById(R.id.fab_capture);
        ImageButton btnGallery = findViewById(R.id.btn_gallery);
        ImageButton btnFlash = findViewById(R.id.btn_flash);
        Button btnCancel = findViewById(R.id.btn_cancel);

        startCamera();
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Handle capture, gallery, and flash toggle actions
        fabCapture.setOnClickListener(v -> takePhoto());
        btnCancel.setOnClickListener(v -> finish());
        btnGallery.setOnClickListener(v -> openGallery());
        btnFlash.setOnClickListener(v -> toggleFlash());
    }

    // Initialize camera with CameraX
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                        .build();

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
            } catch (Exception e) {
                Log.e("CameraXApp", "Camera binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // Capture and save a photo
    private void takePhoto() {
        if (imageCapture == null) return;

        File photoFile = new File(getExternalFilesDir(null), System.currentTimeMillis() + "_photo.jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                runOnUiThread(() -> {
                    Toast.makeText(CameraActivity.this, "Photo captured successfully!", Toast.LENGTH_SHORT).show();
                    if (isFlashOn) toggleFlash();

                    Intent intent = new Intent(CameraActivity.this, WasteActivity.class);
                    intent.putExtra("captured_image_path", photoFile.getAbsolutePath());
                    startActivity(intent);
                });
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(() -> Toast.makeText(CameraActivity.this, "Capture failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    // Toggle the flash
    private void toggleFlash() {
        if (camera != null) {
            isFlashOn = !isFlashOn;
            camera.getCameraControl().enableTorch(isFlashOn);
            runOnUiThread(() -> {
                updateFlashIcon();
                Toast.makeText(this, isFlashOn ? "Flash On" : "Flash Off", Toast.LENGTH_SHORT).show();
            });
        } else {
            Toast.makeText(this, "Flash not available", Toast.LENGTH_SHORT).show();
        }
    }

    // Update the flash icon
    private void updateFlashIcon() {
        ImageButton btnFlash = findViewById(R.id.btn_flash);
        int flashIcon = isFlashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off;
        btnFlash.setImageDrawable(ContextCompat.getDrawable(this, flashIcon));
    }

    // Open the gallery to select an image
    private void openGallery() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryIntent.setType("image/*");
        openGalleryLauncher.launch(galleryIntent);
    }

    // Handle result from gallery selection
    ActivityResultLauncher<Intent> openGalleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        Intent intent = new Intent(CameraActivity.this, WasteActivity.class);
                        intent.putExtra("selected_image_uri", selectedImageUri.toString());
                        if (isFlashOn) toggleFlash();
                        startActivity(intent);
                    }
                }
            });

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}
