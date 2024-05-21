package com.rolandfacialexpression;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {
    private static final int CAMERA_REQUEST_CODE = 1001;
    private static final int GALLERY_REQUEST_CODE = 1002;
    private Interpreter tflite;
    private PreviewView previewView;
    private TextView resultTextView;
    private Button captureButton, selectButton;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageCapture imageCapture;

    private static final String[] emotions = {"Angry", "Disgust", "Fear", "Happy", "Sad", "Surprise", "Neutral"};
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        previewView = findViewById(R.id.view_finder);
        resultTextView = findViewById(R.id.result_text);
        captureButton = findViewById(R.id.capture_button);
        selectButton = findViewById(R.id.select_button);
        // Request camera permissions
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this,
                        android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.CAMERA}, 101);
        } else {
            startCamera();
        }
        try {
            tflite = new Interpreter(loadModelFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                captureAndRecognize();
            }
        });
        selectButton.setOnClickListener(v -> {
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
            try {
                Bitmap bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(selectedImageUri));
                if (bitmap != null && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
                    ByteBuffer inputBuffer = preprocessBitmap(bitmap);
                    float[][] output = runInference(inputBuffer);
                    displayResult(output);
                } else {
                    // Handle null or invalid bitmap
                }
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
            // Example: processSelectedImage(selectedImageUri);
            Toast.makeText(this, "Image selected from gallery", Toast.LENGTH_SHORT).show();
        }
    }

    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                    // Preview configuration
                    Preview preview = new Preview.Builder().build();
                    preview.setSurfaceProvider(previewView.getSurfaceProvider());

                    // Image capture configuration
                    imageCapture = new ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .build();

                    // Bind use cases to lifecycle
                    cameraProvider.bindToLifecycle(MainActivity.this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture);
                } catch (ExecutionException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void captureAndRecognize() {
        if (imageCapture != null) {
            imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
                @Override
                public void onCaptureSuccess(@NonNull ImageProxy image) {
                    Bitmap bitmap = previewView.getBitmap();
                    if (bitmap != null && bitmap.getWidth() > 0 && bitmap.getHeight() > 0) {
                        ByteBuffer inputBuffer = preprocessBitmap(bitmap);
                        float[][] output = runInference(inputBuffer);
                        displayResult(output);
                    } else {
                        // Handle null or invalid bitmap
                    }
                    image.close();
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    exception.printStackTrace();
                }
            });
        }
    }

    private MappedByteBuffer loadModelFile() throws IOException {
        FileInputStream fileInputStream = new FileInputStream(getAssets().openFd("facial_recognition_model.tflite").getFileDescriptor());
        FileChannel fileChannel = fileInputStream.getChannel();
        long startOffset = getAssets().openFd("facial_recognition_model.tflite").getStartOffset();
        long declaredLength = getAssets().openFd("facial_recognition_model.tflite").getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private ByteBuffer preprocessBitmap(Bitmap bitmap) {
        int inputSize = 48; // Adjust this to your model's input size
        ByteBuffer inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 1 * 4);
        inputBuffer.order(ByteOrder.nativeOrder());
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true);
        int[] intValues = new int[inputSize * inputSize];
        scaledBitmap.getPixels(intValues, 0, inputSize, 0, 0, inputSize, inputSize);

        int pixel = 0;
        for (int i = 0; i < inputSize; i++) {
            for (int j = 0; j < inputSize; j++) {
                int value = intValues[pixel++];
                inputBuffer.putFloat((value & 0xFF) / 255.0f); // Assuming grayscale
            }
        }

        return inputBuffer;
    }

    public Bitmap toGrayscale(Bitmap bmpOriginal)
    {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();
        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

    private float[][] runInference(ByteBuffer inputBuffer) {
        float[][] output = new float[1][7]; // Adjust according to your model's output shape
        tflite.run(inputBuffer, output);
        return output;
    }

    private void displayResult(float[][] output) {
        float sum = 0;
        for (float val : output[0]) {
            sum += val;
        }
        // Create an array to store percentages and their corresponding indices
        float[] percentages = new float[output[0].length];
        for (int i = 0; i < output[0].length; i++) {
            percentages[i] = (output[0][i] / sum) * 100;
        }
        // Create an array of indices to sort by percentage
        Integer[] indices = new Integer[percentages.length];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }
        // Sort the indices array based on corresponding percentage values in descending order
        Arrays.sort(indices, (a, b) -> Float.compare(percentages[b], percentages[a]));
        // Build the result string with sorted percentages
        StringBuilder result = new StringBuilder();
        for (int index : indices) {
            result.append(emotions[index])
                    .append(": ")
                    .append(String.format("%.2f", percentages[index]))
                    .append("%\n");
        }

        resultTextView.setText(result.toString());
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == 101 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }
}