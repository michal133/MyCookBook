package com.example.cookbook.ui.fragments;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.cookbook.R;
import com.example.cookbook.ui.activities.MainActivity;
import com.example.cookbook.util.FirebaseManager;
import com.google.firebase.auth.FirebaseUser;

public class ProfileFragment extends Fragment {

    private FirebaseManager firebaseManager;
    private TextView tvEmail;
    private Button btnLogout, btnChangePassword;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        firebaseManager = FirebaseManager.getInstance();

        // Initialize UI components
        tvEmail = view.findViewById(R.id.tvEmail);
        btnLogout = view.findViewById(R.id.btnLogout);
        btnChangePassword = view.findViewById(R.id.btnChangePassword);

        setupUserProfile();
        setupClickListeners();
    }

    private void setupUserProfile() {
        FirebaseUser currentUser = firebaseManager.getCurrentUser();
        if (currentUser != null) {
            tvEmail.setText(currentUser.getEmail());
        }
    }

    private void setupClickListeners() {
        // Handle Logout
        btnLogout.setOnClickListener(v -> handleLogout());

        // Handle Change Password
        btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());
    }

    private void handleLogout() {
        firebaseManager.logoutUser();

        // Go back to Login Screen (MainActivity) and clear history so user can't go back
        Intent intent = new Intent(requireContext(), MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    private void showChangePasswordDialog() {
        // Create a simple input field for the new password
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("New Password");

        new AlertDialog.Builder(requireContext())
                .setTitle("Change Password")
                .setMessage("Enter your new password:")
                .setView(input)
                .setPositiveButton("Change", (dialog, which) -> {
                    String newPassword = input.getText().toString().trim();

                    if (newPassword.length() < 6) {
                        Toast.makeText(requireContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    updatePassword(newPassword);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updatePassword(String newPassword) {
        FirebaseUser user = firebaseManager.getCurrentUser();
        if (user != null) {
            user.updatePassword(newPassword)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(requireContext(), "Password updated successfully", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(requireContext(), "Failed to update password", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }
}