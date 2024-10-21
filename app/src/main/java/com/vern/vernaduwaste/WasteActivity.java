package com.vern.vernaduwaste;

import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class WasteActivity extends AppCompatActivity {

    private Interpreter tflite;
    private ImageView imageView;
    private final String[] wasteTypes = {"Biodegradable", "Non-biodegradable", "Recyclable"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waste);

        imageView = findViewById(R.id.captured_image_view);
        TextView resultTextView = findViewById(R.id.text_waste_type);

        // Load TensorFlow Lite model
        try {
            tflite = new Interpreter(loadModelFile());
        } catch (IOException e) {
            Log.e("WasteActivity", "Error loading TensorFlow Lite model", e);
            Toast.makeText(this, "Model loading failed!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Get image from intent and classify it
        String imagePath = getIntent().getStringExtra("captured_image_path");
        String selectedImageUri = getIntent().getStringExtra("selected_image_uri");

        if (imagePath != null) {
            displayCapturedImage(imagePath);
        } else if (selectedImageUri != null) {
            displayGalleryImage(Uri.parse(selectedImageUri));
        } else {
            Toast.makeText(this, "No image found!", Toast.LENGTH_SHORT).show();
        }
    }

    // Load the TensorFlow Lite model
    private MappedByteBuffer loadModelFile() throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd("waste_classifier.tflite");
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    // Classify the waste
    private void classifyWaste(Bitmap bitmap) {
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true);
        TensorImage tensorImage = new TensorImage(org.tensorflow.lite.DataType.FLOAT32);
        tensorImage.load(resizedBitmap);

        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(new int[]{1, 3}, org.tensorflow.lite.DataType.FLOAT32);
        tflite.run(tensorImage.getBuffer(), outputBuffer.getBuffer().rewind());

        String result = getWasteType(outputBuffer.getFloatArray());
        runOnUiThread(() -> ((TextView) findViewById(R.id.text_waste_type)).setText(result));
    }

    private String getWasteType(float[] output) {
        int maxIndex = 0;
        for (int i = 1; i < output.length; i++) {
            if (output[i] > output[maxIndex]) maxIndex = i;
        }
        return wasteTypes[maxIndex];
    }

    private void displayCapturedImage(String imagePath) {
        File imageFile = new File(imagePath);
        if (imageFile.exists()) {
            imageView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
                Bitmap squareBitmap = cropToSquare(bitmap);
                imageView.setImageBitmap(squareBitmap);
                classifyWaste(squareBitmap);
            });
        }
    }

    private void displayGalleryImage(Uri imageUri) {
        imageView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                Bitmap squareBitmap = cropToSquare(bitmap);
                imageView.setImageBitmap(squareBitmap);
                classifyWaste(squareBitmap);
            } catch (IOException e) {
                Log.e("WasteActivity", "Error loading gallery image", e);
            }
        });
    }

    private Bitmap cropToSquare(Bitmap bitmap) {
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int newWidth = Math.min(width, height);
        int newHeight = Math.min(width, height);
        int cropW = (width - newWidth) / 2;
        int cropH = (height - newHeight) / 2;
        return Bitmap.createBitmap(bitmap, cropW, cropH, newWidth, newHeight);
    }
}