package com.example.cookbook.ui.activities;

import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.cookbook.R;
import com.example.cookbook.ui.fragments.FavoritesFragment;
import com.example.cookbook.ui.fragments.HomeFragment;
import com.example.cookbook.ui.fragments.ProfileFragment;
import com.example.cookbook.util.FirebaseManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationBarView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

public class MainActivity extends AppCompatActivity implements NavigationBarView.OnItemSelectedListener {

    private FirebaseManager firebaseManager;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        firebaseManager = FirebaseManager.getInstance();

        // Check if user is already logged in
        // If yes, skip login screen and go to main app
        if (firebaseManager.getCurrentUser() != null) {
            setupMainAppUI(savedInstanceState);
        } else {
            setupLoginScreen();
        }
    }

    // Setup Login & Register Screen
    private void setupLoginScreen() {
        setContentView(R.layout.activity_main); // Load login layout

        // Initialize UI elements
        TextInputEditText etEmail = findViewById(R.id.etEmail);
        TextInputEditText etPassword = findViewById(R.id.etPassword);
        TextInputLayout tilEmail = findViewById(R.id.tilEmail);
        TextInputLayout tilPassword = findViewById(R.id.tilPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnRegister = findViewById(R.id.btnRegister);
        TextView btnForgotPassword = findViewById(R.id.btnForgotPassword);
        progressBar = findViewById(R.id.progressBar);

        // Handle Login Button Click
        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (validateInput(tilEmail, tilPassword, email, password)) {
                progressBar.setVisibility(View.VISIBLE);
                firebaseManager.loginUser(email, password).addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show();
                        setupMainAppUI(null);
                    } else {
                        Toast.makeText(this, "Login failed", Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        // Handle Register Button Click
        btnRegister.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (validateInput(tilEmail, tilPassword, email, password)) {
                progressBar.setVisibility(View.VISIBLE);
                firebaseManager.registerUser(email, password).addOnCompleteListener(task -> {
                    progressBar.setVisibility(View.GONE);
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Registration successful!", Toast.LENGTH_SHORT).show();
                        setupMainAppUI(null);
                    } else {
                        Toast.makeText(this, "Registration failed: " + task.getException().getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });

        // Handle Forgot Password Click
        btnForgotPassword.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            if (email.isEmpty()) {
                tilEmail.setError("Enter email first");
                return;
            }
            firebaseManager.sendPasswordResetEmail(email).addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Reset link sent to email", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "Failed to send email", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    //Setup Main App UI (Bottom Navigation)
    private void setupMainAppUI(Bundle savedInstanceState) {
        setContentView(R.layout.main_app); // Switch to main app layout

        BottomNavigationView bottomNavigation = findViewById(R.id.bottom_navigation);
        bottomNavigation.setOnItemSelectedListener(this);

        // Load Home Fragment by default on first launch
        if (savedInstanceState == null) {
            loadFragment(new HomeFragment());
        }
    }

    // Basic input validation
    private boolean validateInput(TextInputLayout tilEmail, TextInputLayout tilPassword, String email, String password) {
        if (email.isEmpty()) {
            tilEmail.setError("Email is required");
            return false;
        }
        tilEmail.setError(null);

        if (password.isEmpty() || password.length() < 6) {
            tilPassword.setError("Password must be 6+ chars");
            return false;
        }
        tilPassword.setError(null);
        return true;
    }

    // Handle Bottom Navigation Clicks
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        Fragment fragment = null;
        int itemId = item.getItemId();

        // Match IDs with bottom_navigation_menu.xml
        if (itemId == R.id.navigation_home) {
            fragment = new HomeFragment();
        } else if (itemId == R.id.navigation_favorites) {
            fragment = new FavoritesFragment();
        } else if (itemId == R.id.navigation_profile) {
            fragment = new ProfileFragment();
        }

        return loadFragment(fragment);
    }

    // Helper method to switch fragments
    private boolean loadFragment(Fragment fragment) {
        if (fragment != null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragment_container, fragment)
                    .commit();
            return true;
        }
        return false;
    }
}