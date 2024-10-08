package com.vern.vernaduwaste;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.ViewTreeObserver;
import android.widget.Button;
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

    private static final String TAG = "WasteActivity";
    private String classificationResult = "Unknown";
    private ImageView imageView;
    private Interpreter tflite;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waste);

        imageView = findViewById(R.id.captured_image_view);
        TextView resultTextView = findViewById(R.id.text_waste_type);
        Button disposeButton = findViewById(R.id.btn_dispose);
        Button goBackButton = findViewById(R.id.btn_go_back);

        try {
            tflite = new Interpreter(loadModelFile());
        } catch (IOException e) {
            Log.e(TAG, "Error loading TensorFlow Lite model", e);
            Toast.makeText(this, "Model loading failed!", Toast.LENGTH_SHORT).show();
            return;
        }

        String imagePath = getIntent().getStringExtra("captured_image_path");
        String selectedImageUri = getIntent().getStringExtra("selected_image_uri");

        if (imagePath != null) {
            displayCapturedImage(imagePath);
        } else if (selectedImageUri != null) {
            displayGalleryImage(Uri.parse(selectedImageUri));
        } else {
            Toast.makeText(this, "No image found!", Toast.LENGTH_SHORT).show();
        }

        resultTextView.setText(classificationResult);
        disposeButton.setOnClickListener(v -> Toast.makeText(WasteActivity.this, "Dispose clicked", Toast.LENGTH_SHORT).show());
        goBackButton.setOnClickListener(v -> finish());
    }

    // Load TensorFlow Lite model from assets
    private MappedByteBuffer loadModelFile() throws IOException {
        FileInputStream fileInputStream = new FileInputStream(getAssets().openFd("waste_classifier.tflite").getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = getAssets().openFd("waste_classifier.tflite").getStartOffset();
        long declaredLength = getAssets().openFd("waste_classifier.tflite").getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void classifyWaste(Bitmap bitmap) {
        if (tflite == null) return;

        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true);
        TensorImage tensorImage = new TensorImage(org.tensorflow.lite.DataType.FLOAT32);
        tensorImage.load(resizedBitmap);

        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(new int[]{1, 3}, org.tensorflow.lite.DataType.FLOAT32);
        tflite.run(tensorImage.getBuffer(), outputBuffer.getBuffer().rewind());

        classificationResult = getWasteTypeFromOutput(outputBuffer.getFloatArray());
        runOnUiThread(() -> {
            TextView resultTextView = findViewById(R.id.text_waste_type);
            resultTextView.setText(classificationResult);
        });
    }

    private String getWasteTypeFromOutput(float[] outputValues) {
        int maxIndex = -1;
        float maxConfidence = 0;
        String[] wasteTypes = {"Biodegradable", "Non-biodegradable", "Recyclable"};

        for (int i = 0; i < outputValues.length; i++) {
            if (outputValues[i] > maxConfidence) {
                maxConfidence = outputValues[i];
                maxIndex = i;
            }
        }

        return maxIndex != -1 ? wasteTypes[maxIndex] : "Unknown";
    }

    private void displayCapturedImage(String imagePath) {
        File imageFile = new File(imagePath);
        if (imageFile.exists()) {
            imageView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                Bitmap bitmap = decodeSampledBitmapFromFile(imagePath, imageView.getWidth(), imageView.getHeight());
                Bitmap squareBitmap = cropToSquare(bitmap);
                imageView.setImageBitmap(squareBitmap);
                classifyWaste(squareBitmap);
            });
        }
    }

    private void displayGalleryImage(Uri selectedImageUri) {
        imageView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
                Bitmap squareBitmap = cropToSquare(bitmap);
                imageView.setImageBitmap(squareBitmap);
                classifyWaste(squareBitmap);
            } catch (Exception e) {
                Log.e(TAG, "Error loading gallery image", e);
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

    public Bitmap decodeSampledBitmapFromFile(String filePath, int reqWidth, int reqHeight) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(filePath, options);
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(filePath, options);
    }

    public int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;
        if (height > reqHeight || width > reqWidth) {
            final int halfHeight = height / 2;
            final int halfWidth = width / 2;
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }
}
