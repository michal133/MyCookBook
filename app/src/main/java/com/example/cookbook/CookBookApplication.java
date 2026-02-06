package com.example.cookbook;

import android.app.Application;
import com.google.firebase.FirebaseApp;

public class CookBookApplication extends Application {

    private static CookBookApplication instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Initialize Firebase just to be safe
        try {
            FirebaseApp.initializeApp(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Static method to get context anywhere
    public static CookBookApplication getInstance() {
        return instance;
    }
}