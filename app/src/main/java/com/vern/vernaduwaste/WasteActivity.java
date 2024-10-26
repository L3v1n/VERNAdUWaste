package com.vern.vernaduwaste;

import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
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
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class WasteActivity extends AppCompatActivity {
    private Interpreter tflite;
    private final String[] wasteTypes = {"Biodegradable", "Non-biodegradable", "Recyclable"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waste);

        ImageView imageView = findViewById(R.id.captured_image_view);
        TextView resultTextView = findViewById(R.id.text_waste_type);
        Button btnGoBack = findViewById(R.id.btn_go_back);
        Button btnDispose = findViewById(R.id.btn_dispose);

        // Set up the go-back button to return to CameraActivity
        btnGoBack.setOnClickListener(v -> finish());

        // Set up the button to navigate to the disposal map
        btnDispose.setOnClickListener(v -> startActivity(new Intent(WasteActivity.this, NavigationMapActivity.class)));

        // Initialize the TensorFlow Lite interpreter
        try {
            tflite = new Interpreter(loadModelFile());
        } catch (IOException e) {
            Toast.makeText(this, "Model loading failed!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Retrieve and display the image either from the camera or the gallery
        String imagePath = getIntent().getStringExtra("captured_image_path");
        String selectedImageUri = getIntent().getStringExtra("selected_image_uri");

        if (imagePath != null) {
            displayImageAndClassify(imagePath, imageView, resultTextView);
        } else if (selectedImageUri != null) {
            displayImageFromUri(Uri.parse(selectedImageUri), imageView, resultTextView);
        } else {
            Toast.makeText(this, "No image found!", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Loads the TensorFlow Lite model from the assets folder.
     *
     * @return MappedByteBuffer of the model.
     * @throws IOException If the model file cannot be loaded.
     */
    private MappedByteBuffer loadModelFile() throws IOException {
        try (AssetFileDescriptor fileDescriptor = getAssets().openFd("waste_classifier.tflite");
             FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
             FileChannel fileChannel = inputStream.getChannel()) {

            long startOffset = fileDescriptor.getStartOffset();
            long declaredLength = fileDescriptor.getDeclaredLength();
            return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
        }
    }

    /**
     * Classifies the waste type using the TensorFlow Lite model.
     *
     * @param bitmap          The image bitmap to classify.
     * @param resultTextView  The TextView to display the classification result.
     */
    private void classifyWaste(Bitmap bitmap, TextView resultTextView) {
        // Preprocess the image to match the model input size
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, 224, 224, true);
        TensorImage tensorImage = new TensorImage(org.tensorflow.lite.DataType.FLOAT32);
        tensorImage.load(resizedBitmap);

        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(new int[]{1, 3}, org.tensorflow.lite.DataType.FLOAT32);
        tflite.run(tensorImage.getBuffer(), outputBuffer.getBuffer().rewind());

        // Get the result from the model and display it
        resultTextView.setText(getWasteType(outputBuffer.getFloatArray()));
    }

    /**
     * Determines the waste type from the model output.
     *
     * @param output The array of output probabilities.
     * @return The waste type with the highest probability.
     */
    private String getWasteType(float[] output) {
        int maxIndex = 0;
        for (int i = 1; i < output.length; i++) {
            if (output[i] > output[maxIndex]) maxIndex = i;
        }
        return wasteTypes[maxIndex];
    }

    /**
     * Displays the image captured from the camera and classifies it.
     *
     * @param imagePath The file path of the captured image.
     * @param imageView The ImageView to display the image.
     * @param resultTextView The TextView to display the classification result.
     */
    private void displayImageAndClassify(String imagePath, ImageView imageView, TextView resultTextView) {
        File imageFile = new File(imagePath);
        if (imageFile.exists()) {
            Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
            imageView.setImageBitmap(bitmap); // Directly set the bitmap
            classifyWaste(bitmap, resultTextView);
        }
    }

    /**
     * Displays the image selected from the gallery and classifies it.
     *
     * @param uri The URI of the selected image.
     * @param imageView The ImageView to display the image.
     * @param resultTextView The TextView to display the classification result.
     */
    private void displayImageFromUri(Uri uri, ImageView imageView, TextView resultTextView) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap); // Directly set the bitmap
                classifyWaste(bitmap, resultTextView);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Failed to load image", Toast.LENGTH_SHORT).show();
        }
    }
}
