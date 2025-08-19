package com.frontendsource.drugstore.activity;

import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;


import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
//import com.google.protobuf.ByteString;

import org.apache.commons.text.similarity.LevenshteinDistance;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.googlecode.tesseract.android.TessBaseAPI;
import com.yalantis.ucrop.UCrop;

public class PrescriptionsActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 201;
    private static final int GALLERY_PERMISSION_REQUEST = 202;
    private Uri imageUri;

    private TextView resultText;
    private Button scanBtn;
    ImageView imageView;

    // Modern gallery picker
    private final ActivityResultLauncher<String> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            uri -> {
                if (uri != null) {
                    startCrop(uri);
                }
            });
    private List<String> medicineDictionary;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);


        resultText = findViewById(R.id.resultText);
        scanBtn = findViewById(R.id.scanBtn);
        imageView = findViewById(R.id.imageview);
//        medicineDictionary = loadMedicineDictionary();

        scanBtn.setOnClickListener(v -> showImagePickerDialog());
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST);
        } else {
            openCamera();
        }
    }
    private void showImagePickerDialog() {
        String[] options = {"Camera", "Gallery"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("Select Image")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) { // Camera
                        checkCameraPermission();
                    } else { // Gallery
                        checkGalleryPermission();
                    }
                })
                .show();
    }
    private void checkGalleryPermission() {
//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
//                != PackageManager.PERMISSION_GRANTED) {
//            ActivityCompat.requestPermissions(this,
//                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
//                    GALLERY_PERMISSION_REQUEST);
//        } else {
            pickFromGallery();
//        }
    }
    private void openCamera() {
        File photoFile = new File(getCacheDir(), "camera_" + System.currentTimeMillis() + ".jpg");
        imageUri = FileProvider.getUriForFile( this, getApplicationContext().getPackageName() + ".fileprovider", photoFile);
        Intent cameraIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(cameraIntent, 101);
    }
    private final ActivityResultLauncher<Uri> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), result -> {
                if (result) {
                    startCrop(imageUri); // Start cropping after picture is taken
                } else {
                    Toast.makeText(this, "Camera capture cancelled", Toast.LENGTH_SHORT).show();
                }
            });
    private void pickFromGallery() {
        galleryLauncher.launch("image/*");
    }

    // Crop using UCrop
    private void startCrop(Uri sourceUri) {
        File destFile = new File(getCacheDir(), "cropped_" + System.currentTimeMillis() + ".jpg");

        UCrop.Options options = new UCrop.Options();
        options.setFreeStyleCropEnabled(true);        // Allow free crop
        options.setToolbarTitle("Adjust Crop");       // Optional title
        options.setToolbarColor(getResources().getColor(R.color.purple_500));
        options.setStatusBarColor(getResources().getColor(R.color.purple_700));
        options.setActiveControlsWidgetColor(getResources().getColor(R.color.teal_200));

        // Start UCrop without fixed aspect ratio
        UCrop.of(sourceUri, Uri.fromFile(destFile))
                .withOptions(options)
                .start(this);
    }
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == GALLERY_PERMISSION_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickFromGallery();
            } else {
                Toast.makeText(this, "Storage permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK ){

            if (requestCode == 101) { // Camera finished
                startCrop(imageUri); // Start cropping the captured image
            } else if(requestCode == UCrop.REQUEST_CROP) {
                final Uri resultUri = UCrop.getOutput(data);
                if (resultUri != null) {
                    imageUri = resultUri;
                    try {
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
//                        Bitmap bitmap1 = preprocessBitmap(bitmap);

                        imageView.setImageBitmap(bitmap);
                        runTextRecognition(bitmap);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } else if (resultCode == UCrop.RESULT_ERROR) {
            final Throwable cropError = UCrop.getError(data);
            Toast.makeText(this, "Crop error: " + cropError, Toast.LENGTH_SHORT).show();
        }
    }

    private void runTextRecognition(Bitmap bitmap) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(image)
                .addOnSuccessListener(result ->
                {
                    StringBuilder extracted = new StringBuilder();
                    for (Text.TextBlock block : result.getTextBlocks())
                    {
                        extracted.append(block.getText()).append("\n");
                    }
                    if (extracted.length() == 0) {
                        resultText.setText("❌ No text detected. Try a clearer image.");
                    } else {
                        resultText.setText(extracted.toString());
                    }
                }) .addOnFailureListener(e -> {
                    resultText.setText("⚠️ Error: " + e.getMessage());
                    Log.e("OCR", "Failed: ", e);
                });
    }

  }