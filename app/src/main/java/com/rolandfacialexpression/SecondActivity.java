package com.rolandfacialexpression;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class SecondActivity extends AppCompatActivity {
    private static final int CAMERA_REQUEST_CODE = 1001;
    private static final int GALLERY_REQUEST_CODE = 1002;
    private Interpreter interpreter;
    private Button captureButton;
    private Button galleryButton;
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.second);
        captureButton = findViewById(R.id.capture_button);
        galleryButton = findViewById(R.id.gallery_button);

        // Load your TensorFlow Lite model
        try {
            interpreter = new Interpreter(loadModelFile(), new Interpreter.Options());
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Request camera and storage permissions if not granted
        if (ContextCompat.checkSelfPermission(this,
                android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA, android.Manifest.permission.READ_EXTERNAL_STORAGE},
                    CAMERA_REQUEST_CODE);
        }

        captureButton.setOnClickListener(v -> {
            // Open camera for photo capture
            Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE);
        });

        galleryButton.setOnClickListener(v -> {
            // Open gallery for image selection
            Intent galleryIntent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CAMERA_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // Handle captured photo from camera
            // Example: processCapturedImage(data.getExtras().getParcelable("data"));
            Toast.makeText(this, "Photo captured from camera", Toast.LENGTH_SHORT).show();
        } else if (requestCode == GALLERY_REQUEST_CODE && resultCode == RESULT_OK && data != null) {
            // Handle selected image from gallery
            Uri selectedImageUri = data.getData();
            // Example: processSelectedImage(selectedImageUri);
            Toast.makeText(this, "Image selected from gallery", Toast.LENGTH_SHORT).show();
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        FileInputStream fileInputStream = new FileInputStream(getAssets().openFd("model.tflite").getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = getAssets().openFd("model.tflite").getStartOffset();
        long declaredLength = getAssets().openFd("model.tflite").getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void processCapturedImage(Uri imageUri) {
        // Process the captured image (e.g., resize, convert to grayscale, normalize)
        // Then pass it to your model for prediction
    }

    private void processSelectedImage(Uri imageUri) {
        // Process the selected image (e.g., resize, convert to grayscale, normalize)
        // Then pass it to your model for prediction
    }

//    private void processAndPredict(Uri imageUri) {
//        try {
//            // Load and process the image
//            Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
//            Bitmap processedBitmap = preprocessImage(bitmap);
//
//            // Display the processed image
//            imageView.setImageBitmap(processedBitmap);
//
//            // Pass the processed image to the model for prediction
//            float[] inputValues = preprocessForModel(processedBitmap);
//            float[] outputValues = new float[outputSize]; // Adjust outputSize based on your model's output
//
//            interpreter.run(inputValues, outputValues);
//
//            // Process outputValues (e.g., get predicted emotion)
//            // Example: float predictedEmotionIndex = getPredictedEmotionIndex(outputValues);
//            // Display or use the predicted emotion
//        } catch (IOException e) {
//            e.printStackTrace();
//            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show();
//        }
//    }
//
//    private Bitmap preprocessImage(Bitmap bitmap) {
//        // Resize, convert to grayscale, normalize, or apply other preprocessing steps
//        // Example:
//         Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, inputWidth, inputHeight, true);
//         Bitmap grayscaleBitmap = toGrayscale(resizedBitmap);
//         Bitmap normalizedBitmap = normalize(grayscaleBitmap);
//        return bitmap; // Return the processed bitmap
//    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                    grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Camera and storage permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Camera and storage permissions required", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
