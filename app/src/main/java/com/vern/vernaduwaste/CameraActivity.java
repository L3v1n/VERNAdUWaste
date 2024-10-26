package com.vern.vernaduwaste;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private CameraControl cameraControl;
    private Camera camera;
    private ExecutorService cameraExecutor;
    private ImageButton btnFlash;
    private boolean isFlashOn = false;

    // ActivityResultLauncher for the Photo Picker API
    private final ActivityResultLauncher<Intent> photoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        handleImageUri(selectedImageUri);
                        resetFlashState(); // Reset flash state after selecting photo
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        previewView = findViewById(R.id.previewView);
        FloatingActionButton fabCapture = findViewById(R.id.fab_capture);
        ImageButton btnGallery = findViewById(R.id.btn_gallery);
        btnFlash = findViewById(R.id.btn_flash);
        Button btnCancel = findViewById(R.id.btn_cancel);

        // Start camera setup and executor service
        startCamera();
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Capture image on button click
        fabCapture.setOnClickListener(v -> takePhoto());

        // Open gallery on button click
        btnGallery.setOnClickListener(v -> openPhotoPicker());

        // Toggle flash on button click
        btnFlash.setOnClickListener(v -> toggleFlash());

        // Cancel button to close the activity
        btnCancel.setOnClickListener(v -> finish());

        // Set up touch to focus functionality
        previewView.setOnTouchListener(this::handleFocus);
    }

    /**
     * Sets up camera preview and binds lifecycle.
     */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Build image capture use case
                imageCapture = new ImageCapture.Builder().build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // Unbind any previous use cases and bind the new ones
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
                cameraControl = camera.getCameraControl();

                updateFlashButtonIcon(); // Update flash button based on flash state
            } catch (Exception e) {
                Toast.makeText(this, "Failed to start camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Captures an image using the camera and saves it locally.
     */
    private void takePhoto() {
        if (imageCapture == null) return;

        File photoFile = new File(getExternalFilesDir(null), System.currentTimeMillis() + "_photo.jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                // Send the captured image to the WasteActivity for classification
                Intent intent = new Intent(CameraActivity.this, WasteActivity.class);
                intent.putExtra("captured_image_path", photoFile.getAbsolutePath());
                startActivity(intent);
                resetFlashState(); // Reset flash state after taking photo
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Toast.makeText(CameraActivity.this, "Capture failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Opens the Photo Picker on Android 13+ and the legacy gallery on older versions.
     */
    private void openPhotoPicker() {
        // Turn off flash before opening the photo picker
        resetFlashState();

        Intent intent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            // Use the new photo picker API on Android 13+ (API level 33)
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        } else {
            // Fallback to the legacy image picker for older versions
            intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }

        // Launch the photo picker
        photoPickerLauncher.launch(intent);
    }

    /**
     * Processes each selected image URI.
     */
    private void handleImageUri(Uri uri) {
        Intent intent = new Intent(CameraActivity.this, WasteActivity.class);
        intent.putExtra("selected_image_uri", uri.toString());
        startActivity(intent);
    }

    /**
     * Toggles the flash on and off.
     */
    private void toggleFlash() {
        if (camera != null) {
            isFlashOn = !isFlashOn;
            cameraControl.enableTorch(isFlashOn);
            updateFlashButtonIcon();
        }
    }

    /**
     * Resets the flash state and icon to off.
     */
    private void resetFlashState() {
        if (isFlashOn) {
            isFlashOn = false;
            cameraControl.enableTorch(false);
            updateFlashButtonIcon();
        }
    }

    /**
     * Updates the flash button icon based on the current flash state.
     */
    private void updateFlashButtonIcon() {
        btnFlash.setImageResource(isFlashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
    }

    /**
     * Handles touch-to-focus functionality.
     */
    private boolean handleFocus(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            MeteringPointFactory meteringPointFactory = previewView.getMeteringPointFactory();
            MeteringPoint focusPoint = meteringPointFactory.createPoint(event.getX(), event.getY());
            cameraControl.startFocusAndMetering(
                    new FocusMeteringAction.Builder(focusPoint, FocusMeteringAction.FLAG_AF).build());
            return true;
        }
        return false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown(); // Shut down the executor to release resources
    }
}
