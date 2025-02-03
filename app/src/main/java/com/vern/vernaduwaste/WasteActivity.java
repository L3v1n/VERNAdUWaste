package com.vern.vernaduwaste;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.image.ops.ResizeOp;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.MappedByteBuffer;
import java.util.Arrays;

public class WasteActivity extends AppCompatActivity {
    private static final String TAG = "WasteActivity";
    private static final String MODEL_FILENAME = "waste_classifier.tflite";

    // Define class labels in the exact order as the model's output
    private static final String[] WASTE_TYPES = {"Biodegradable", "Non-biodegradable", "Recyclable", "Not recognized"};

    // Model input image size
    private static final int MODEL_INPUT_SIZE = 224;

    // Confidence threshold to consider a prediction valid
    private static final float CONFIDENCE_THRESHOLD = 0.5f;

    private Interpreter tflite;
    private Button btnDispose;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_waste);
        Log.d(TAG, "WasteActivity started.");

        // Initialize UI components
        ImageView imageView = findViewById(R.id.captured_image_view);
        TextView resultTextView = findViewById(R.id.text_waste_type);
        Button btnGoBack = findViewById(R.id.btn_go_back);
        btnDispose = findViewById(R.id.btn_dispose);

        // Set up button listeners
        btnGoBack.setOnClickListener(v -> finish());

        btnDispose.setOnClickListener(v -> {
            Intent intent = new Intent(WasteActivity.this, NavigationMapActivity.class);
            startActivity(intent);
            Log.d(TAG, "Navigated to NavigationMapActivity.");
        });

        // Load the TFLite model
        try {
            tflite = new Interpreter(loadModelFile());
            Log.d(TAG, "TensorFlow Lite model loaded successfully.");
        } catch (IOException e) {
            Log.e(TAG, "Model loading failed!", e);
            Toast.makeText(this, "Failed to load model.", Toast.LENGTH_LONG).show();
            btnDispose.setEnabled(false);
            return;
        }

        // Retrieve image paths from Intent extras
        String imagePath = getIntent().getStringExtra("captured_image_path");
        String selectedImageUri = getIntent().getStringExtra("selected_image_uri");

        // Display and classify the image based on the available data
        if (imagePath != null) {
            displayAndClassifyImage(imagePath, imageView, resultTextView);
        } else if (selectedImageUri != null) {
            displayAndClassifyImage(Uri.parse(selectedImageUri), imageView, resultTextView);
        } else {
            Log.w(TAG, "No image found to display.");
            resultTextView.setText("Not recognized");
            btnDispose.setEnabled(false);
            Toast.makeText(this, "No image provided for classification.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Loads the TFLite model from the assets folder.
     *
     * @return MappedByteBuffer of the model file.
     * @throws IOException if the model file is not found or cannot be read.
     */
    private MappedByteBuffer loadModelFile() throws IOException {
        return FileUtil.loadMappedFile(this, MODEL_FILENAME);
    }

    /**
     * Classifies the given bitmap image and updates the UI with the result.
     *
     * @param bitmap          The image to classify.
     * @param resultTextView  The TextView to display the classification result.
     */
    private void classifyWaste(Bitmap bitmap, TextView resultTextView) {
        if (bitmap == null) {
            Log.e(TAG, "Bitmap is null. Cannot classify.");
            resultTextView.setText("Not recognized");
            btnDispose.setEnabled(false);
            return;
        }

        // Preprocess the image
        TensorImage tensorImage = preprocessImage(bitmap);

        // Prepare output buffer (Assuming the model outputs probabilities for each class)
        TensorBuffer outputBuffer = TensorBuffer.createFixedSize(new int[]{1, WASTE_TYPES.length}, DataType.FLOAT32);

        // Run inference
        try {
            tflite.run(tensorImage.getBuffer(), outputBuffer.getBuffer().rewind());
            Log.d(TAG, "Inference completed successfully.");
        } catch (Exception e) {
            Log.e(TAG, "Error during model inference.", e);
            Toast.makeText(this, "Error during classification.", Toast.LENGTH_SHORT).show();
            resultTextView.setText("Not recognized");
            btnDispose.setEnabled(false);
            return;
        }

        // Process the output
        float[] output = outputBuffer.getFloatArray();
        Log.d(TAG, "Model output: " + Arrays.toString(output));

        // Display the classification result
        displayClassificationResult(output, resultTextView);
    }

    /**
     * Preprocesses the bitmap image to match the model's input requirements.
     *
     * @param bitmap The original bitmap image.
     * @return Preprocessed TensorImage.
     */
    private TensorImage preprocessImage(Bitmap bitmap) {
        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(bitmap);

        // Define image processing steps
        ImageProcessor imageProcessor = new ImageProcessor.Builder()
                .add(new ResizeOp(MODEL_INPUT_SIZE, MODEL_INPUT_SIZE, ResizeOp.ResizeMethod.BILINEAR))
                .add(new NormalizeOp(0f, 1f)) // Normalize to [0,1]
                .build();

        // Apply processing
        tensorImage = imageProcessor.process(tensorImage);
        return tensorImage;
    }

    /**
     * Determines the classification result based on the model's output probabilities.
     *
     * @param output          The array of probabilities from the model.
     * @param resultTextView  The TextView to display the classification result.
     */
    private void displayClassificationResult(float[] output, TextView resultTextView) {
        int maxIndex = -1;
        float maxProbability = -1f;

        // Identify the class with the highest probability
        for (int i = 0; i < output.length; i++) {
            if (output[i] > maxProbability) {
                maxProbability = output[i];
                maxIndex = i;
            }
        }

        // Log all class probabilities
        for (int i = 0; i < WASTE_TYPES.length; i++) {
            Log.d(TAG, String.format("Class '%s': %.4f", WASTE_TYPES[i], output[i]));
        }

        // Determine if the prediction meets the confidence threshold
        if (maxIndex == -1 || WASTE_TYPES[maxIndex].equals("Not recognized") || maxProbability < CONFIDENCE_THRESHOLD) {
            resultTextView.setText("Not recognized");
            Log.d(TAG, String.format("Classification result: Not recognized (Confidence: %.4f)", maxProbability));
            btnDispose.setEnabled(false);
            Toast.makeText(this, "Waste not recognized.", Toast.LENGTH_LONG).show();
        } else {
            // Valid classification
            String classification = WASTE_TYPES[maxIndex];
            resultTextView.setText(classification);
            Log.d(TAG, String.format("Classification result: %s (Confidence: %.4f)", classification, maxProbability));
            btnDispose.setEnabled(true);
        }
    }

    /**
     * Displays and classifies an image from a file path.
     *
     * @param imagePath        The path to the image file.
     * @param imageView        The ImageView to display the image.
     * @param resultTextView   The TextView to display the classification result.
     */
    private void displayAndClassifyImage(String imagePath, ImageView imageView, TextView resultTextView) {
        Bitmap bitmap = BitmapFactory.decodeFile(imagePath);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            classifyWaste(bitmap, resultTextView);
            Log.d(TAG, "Image displayed and classified from path: " + imagePath);
        } else {
            handleImageLoadError("Failed to decode image.", resultTextView);
        }
    }

    /**
     * Displays and classifies an image from a URI.
     *
     * @param uri              The URI of the image.
     * @param imageView        The ImageView to display the image.
     * @param resultTextView   The TextView to display the classification result.
     */
    private void displayAndClassifyImage(Uri uri, ImageView imageView, TextView resultTextView) {
        try (InputStream inputStream = getContentResolver().openInputStream(uri)) {
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                classifyWaste(bitmap, resultTextView);
                Log.d(TAG, "Image displayed and classified from URI: " + uri);
            } else {
                handleImageLoadError("Failed to load image from URI.", resultTextView);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error loading image from URI: " + uri, e);
            handleImageLoadError("Error loading image.", resultTextView);
        }
    }

    /**
     * Handles errors encountered during image loading or classification.
     *
     * @param errorMessage     The error message to display.
     * @param resultTextView   The TextView to display the classification result.
     */
    private void handleImageLoadError(String errorMessage, TextView resultTextView) {
        Log.e(TAG, errorMessage);
        resultTextView.setText("Not recognized");
        btnDispose.setEnabled(false);
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Close the TFLite interpreter to release resources
        if (tflite != null) {
            tflite.close();
            Log.d(TAG, "TensorFlow Lite interpreter closed.");
        }
    }
}
