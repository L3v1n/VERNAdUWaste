package com.vern.vernaduwaste;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.MeteringPointFactory;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {
    private static final String TAG = "CameraActivity";
    private static final int REQUEST_CODE_CAMERA_PERMISSION = 1002;

    private PreviewView previewView;
    private ImageCapture imageCapture;
    private CameraControl cameraControl;
    private Camera camera;
    private ExecutorService cameraExecutor;
    private ImageButton btnFlash;
    private boolean isFlashOn = false;
    private ImageView focusIndicator;
    private FloatingActionButton fabCapture;

    // Flag to prevent multiple photo captures
    private boolean isTakingPhoto = false;

    // ActivityResultLauncher for the Photo Picker API
    private final ActivityResultLauncher<Intent> photoPickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri selectedImageUri = result.getData().getData();
                    if (selectedImageUri != null) {
                        handleImageUri(selectedImageUri);
                        resetFlashState();
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        Log.d(TAG, "CameraActivity started.");

        // Initialize UI components
        previewView = findViewById(R.id.previewView);
        fabCapture = findViewById(R.id.fab_capture);
        ImageButton btnGallery = findViewById(R.id.btn_gallery);
        btnFlash = findViewById(R.id.btn_flash);
        Button btnCancel = findViewById(R.id.btn_cancel);
        focusIndicator = findViewById(R.id.focus_indicator);

        // Start the camera
        startCamera();
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Set click listeners
        fabCapture.setOnClickListener(v -> takePhoto());
        btnGallery.setOnClickListener(v -> openPhotoPicker());
        btnFlash.setOnClickListener(v -> toggleFlash());
        btnCancel.setOnClickListener(v -> finish());

        // Set touch listener for focus
        previewView.setOnTouchListener(this::handleFocus);
    }

    /**
     * Starts the camera and binds the lifecycle.
     */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                // Check camera permissions
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA_PERMISSION);
                    return;
                }

                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder().build();
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // Unbind all use cases before rebinding
                cameraProvider.unbindAll();

                // Bind the camera to the lifecycle with the preview and image capture use cases
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
                cameraControl = camera.getCameraControl();

                updateFlashButtonIcon();
                Log.d(TAG, "Camera started successfully.");
            } catch (Exception e) {
                Log.e(TAG, "Failed to start camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Captures a photo and starts WasteActivity with the captured image path.
     * Prevents multiple captures by disabling the capture button during processing.
     */
    private void takePhoto() {
        if (imageCapture == null || isTakingPhoto) {
            // Either camera is not ready or already taking a photo
            return;
        }

        isTakingPhoto = true;
        fabCapture.setEnabled(false); // Disable capture button to prevent multiple clicks

        File photoFile = new File(getExternalFilesDir(null), System.currentTimeMillis() + "_photo.jpg");
        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                runOnUiThread(() -> {
                    Intent intent = new Intent(CameraActivity.this, WasteActivity.class);
                    intent.putExtra("captured_image_path", photoFile.getAbsolutePath());
                    startActivity(intent);
                    resetFlashState();
                    Log.d(TAG, "Photo captured and saved: " + photoFile.getAbsolutePath());
                    isTakingPhoto = false;
                    fabCapture.setEnabled(true); // Re-enable capture button
                });
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                runOnUiThread(() -> {
                    Log.e(TAG, "Capture failed: " + exception.getMessage());
                    // Optionally, show a toast to inform the user
                    Toast.makeText(CameraActivity.this, "Photo capture failed.", Toast.LENGTH_SHORT).show();
                    isTakingPhoto = false;
                    fabCapture.setEnabled(true); // Re-enable capture button
                });
            }
        });
    }

    /**
     * Opens the photo picker to select an image from the gallery.
     */
    private void openPhotoPicker() {
        resetFlashState();
        Intent intent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        } else {
            intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        }
        photoPickerLauncher.launch(intent);
        Log.d(TAG, "Photo picker opened.");
    }

    /**
     * Handles the selected image URI and starts WasteActivity.
     */
    private void handleImageUri(Uri uri) {
        Intent intent = new Intent(CameraActivity.this, WasteActivity.class);
        intent.putExtra("selected_image_uri", uri.toString());
        startActivity(intent);
        Log.d(TAG, "Selected image URI handled: " + uri);
    }

    /**
     * Toggles the camera flash on or off.
     */
    private void toggleFlash() {
        if (camera != null) {
            isFlashOn = !isFlashOn;
            cameraControl.enableTorch(isFlashOn);
            updateFlashButtonIcon();
            Log.d(TAG, "Flash toggled. Flash is now " + (isFlashOn ? "ON" : "OFF"));
        }
    }

    /**
     * Resets the flash state to off.
     */
    private void resetFlashState() {
        if (isFlashOn) {
            isFlashOn = false;
            cameraControl.enableTorch(false);
            updateFlashButtonIcon();
            Log.d(TAG, "Flash state reset to OFF.");
        }
    }

    /**
     * Updates the flash button icon based on the flash state.
     */
    private void updateFlashButtonIcon() {
        btnFlash.setImageResource(isFlashOn ? R.drawable.ic_flash_on : R.drawable.ic_flash_off);
    }

    /**
     * Handles touch events to focus and adjust exposure and white balance.
     * Also shows a focus indicator at the touch location.
     */
    private boolean handleFocus(View view, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();

            MeteringPointFactory meteringPointFactory = previewView.getMeteringPointFactory();
            MeteringPoint focusPoint = meteringPointFactory.createPoint(x, y);

            // Request AF, AE, and AWB at the tapped point.
            FocusMeteringAction.Builder builder =
                    new FocusMeteringAction.Builder(focusPoint, FocusMeteringAction.FLAG_AF)
                            .addPoint(focusPoint, FocusMeteringAction.FLAG_AE)
                            .addPoint(focusPoint, FocusMeteringAction.FLAG_AWB);

            // Build the action without auto-cancel so it stays focused until a new action is performed.
            FocusMeteringAction action = builder.build();

            // Show the focus indicator immediately to give user feedback.
            showFocusIndicatorAt(x, y);

            // Start focus and metering and listen for the result.
            ListenableFuture<FocusMeteringResult> future = cameraControl.startFocusAndMetering(action);
            future.addListener(() -> {
                try {
                    FocusMeteringResult result = future.get();
                    runOnUiThread(() -> {
                        if (result.isFocusSuccessful()) {
                            // Focus succeeded. Fade out the indicator or show success animation.
                            fadeOutFocusIndicator();
                            Log.d(TAG, "Focus success at point: (" + x + ", " + y + ")");
                        } else {
                            // Focus not successful. Keep indicator a bit longer or give error feedback.
                            fadeOutFocusIndicator();
                            Log.w(TAG, "Focus failed at point: (" + x + ", " + y + ")");
                        }
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        // Exception in focusing (camera might be closed or other error)
                        fadeOutFocusIndicator();
                        Log.e(TAG, "Focus metering action failed: " + e.getMessage());
                    });
                }
            }, ContextCompat.getMainExecutor(this));

            return true;
        }
        return false;
    }

    /**
     * Shows the focus indicator at the specified coordinates.
     */
    private void showFocusIndicatorAt(float x, float y) {
        // Position the indicator relative to the preview
        float indicatorX = previewView.getX() + x - (focusIndicator.getWidth() / 2f);
        float indicatorY = previewView.getY() + y - (focusIndicator.getHeight() / 2f);

        focusIndicator.setX(indicatorX);
        focusIndicator.setY(indicatorY);
        focusIndicator.setVisibility(View.VISIBLE);
        focusIndicator.setAlpha(1f);
    }

    /**
     * Fades out the focus indicator after a short delay.
     */
    private void fadeOutFocusIndicator() {
        focusIndicator.animate()
                .alpha(0f)
                .setStartDelay(600) // Slight delay to let user see it
                .setDuration(300)
                .withEndAction(() -> focusIndicator.setVisibility(View.INVISIBLE))
                .start();
    }

    /**
     * Handles the result of permission requests.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
                Log.d(TAG, "Camera permission granted.");
            } else {
                Log.w(TAG, "Camera permission denied.");
                // Optionally, inform the user that the app cannot function without camera permission
                Toast.makeText(this, "Camera permission is required to use this feature.", Toast.LENGTH_LONG).show();
            }
        }
    }

    /**
     * Shuts down the camera executor when the activity is destroyed.
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        Log.d(TAG, "CameraActivity destroyed and executor shut down.");
    }
}
