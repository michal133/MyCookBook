package com.example.cookbook.util;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ImgBBUploadManager {

    private static final String TAG = "ImgBBUploadManager";


    private static final String IMGBB_API_KEY = "e2d5345704140064609272338d41584c"; // Example key, replace if needed
    private static final String IMGBB_API_URL = "https://api.imgbb.com/1/upload";

    private static final OkHttpClient client = new OkHttpClient();

    public interface UploadCallback {
        void onSuccess(String imageUrl);
        void onError(String error);
    }

    public static void uploadImage(final File imageFile, final UploadCallback callback) {
        // Run network operation in a background thread
        new Thread(() -> {
            try {
                // Build the request body with the image and API key
                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("image", imageFile.getName(),
                                RequestBody.create(MediaType.parse("image/*"), imageFile))
                        .addFormDataPart("key", IMGBB_API_KEY)
                        .build();

                // Build the HTTP request
                Request request = new Request.Builder()
                        .url(IMGBB_API_URL)
                        .post(requestBody)
                        .build();

                // Execute the request
                try (Response response = client.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        runOnMainThread(() -> callback.onError("Upload failed: " + response.code()));
                        return;
                    }

                    // Parse the JSON response
                    String responseBody = response.body().string();
                    JSONObject json = new JSONObject(responseBody);

                    if (json.getBoolean("success")) {
                        String imageUrl = json.getJSONObject("data").getString("url");
                        // Return success on Main Thread
                        runOnMainThread(() -> callback.onSuccess(imageUrl));
                    } else {
                        String errorMsg = json.optString("error", "Unknown error");
                        runOnMainThread(() -> callback.onError("Upload failed: " + errorMsg));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error uploading image", e);
                runOnMainThread(() -> callback.onError(e.getMessage()));
            }
        }).start();
    }

    // Helper method to ensure callbacks run on the UI thread
    private static void runOnMainThread(Runnable action) {
        new Handler(Looper.getMainLooper()).post(action);
    }
}