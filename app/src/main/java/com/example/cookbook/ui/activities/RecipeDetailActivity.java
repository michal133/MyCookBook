package com.example.cookbook.ui.activities;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.cookbook.R;
import com.example.cookbook.model.Ingredient;
import com.example.cookbook.model.Recipe;
import com.example.cookbook.util.FirebaseManager;
import com.google.android.material.button.MaterialButton;

public class RecipeDetailActivity extends AppCompatActivity {

    private static final String TAG = "RecipeDetailActivity";
    private Recipe recipe;
    private FirebaseManager firebaseManager;
    private ActivityResultLauncher<Intent> editRecipeLauncher;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_detail);

        firebaseManager = FirebaseManager.getInstance();

        // Register callback for returning from the Edit screen
        editRecipeLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        // Retrieve the updated recipe object and refresh UI
                        Recipe updatedRecipe = (Recipe) result.getData().getSerializableExtra("updated_recipe");
                        if (updatedRecipe != null) {
                            this.recipe = updatedRecipe;
                            refreshUI();
                        }
                    }
                }
        );

        // Retrieve recipe data passed via Intent
        try {
            recipe = (Recipe) getIntent().getSerializableExtra("recipe");
            if (recipe == null) {
                Toast.makeText(this, "Error: Recipe not found", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }

            refreshUI();
            setupActionButtons();

        } catch (Exception e) {
            Log.e(TAG, "Error initializing activity", e);
            finish();
        }
    }

    private void setupActionButtons() {
        MaterialButton btnDeleteRecipe = findViewById(R.id.btnDeleteRecipe);
        MaterialButton btnEditRecipe = findViewById(R.id.btnEditRecipe);

        String currentUserId = firebaseManager.getCurrentUserId();

        // Check if the current user is the owner and if it's not an API recipe
        boolean isOwner = recipe.getUserId() != null && recipe.getUserId().equals(currentUserId);
        boolean isApiRecipe = recipe.isImportedFromApi();

        if (isOwner && !isApiRecipe) {
            // Show buttons for owner
            btnEditRecipe.setVisibility(View.VISIBLE);
            btnDeleteRecipe.setVisibility(View.VISIBLE);

            btnEditRecipe.setOnClickListener(v -> {
                Intent intent = new Intent(this, AddRecipeActivity.class);
                intent.putExtra("edit_recipe", true);
                intent.putExtra("recipe", recipe);
                editRecipeLauncher.launch(intent);
            });

            btnDeleteRecipe.setOnClickListener(v -> showDeleteConfirmationDialog());
        } else {
            // Hide buttons for viewers or API recipes
            btnEditRecipe.setVisibility(View.GONE);
            btnDeleteRecipe.setVisibility(View.GONE);
        }
    }


//      Updates the UI elements with the current recipe data

    private void refreshUI() {
        if (recipe == null) return;

        TextView tvTitle = findViewById(R.id.tvTitle);
        TextView tvCategory = findViewById(R.id.tvCategory);
        TextView tvIngredients = findViewById(R.id.tvIngredients);
        TextView tvInstructions = findViewById(R.id.tvInstructions);
        ImageView ivRecipe = findViewById(R.id.ivRecipe);

        tvTitle.setText(recipe.getTitle());
        tvCategory.setText(recipe.getCategory());
        tvInstructions.setText(recipe.getInstructions());

        // Format ingredients list
        StringBuilder ingredientsText = new StringBuilder();
        if (recipe.getIngredients() != null) {
            for (Ingredient ingredient : recipe.getIngredients()) {
                ingredientsText.append("• ")
                        .append(ingredient.getAmount()).append(" ")
                        .append(ingredient.getUnit()).append(" ")
                        .append(ingredient.getName()).append("\n");
            }
        }
        tvIngredients.setText(ingredientsText.toString());

        // Load image using Glide
        if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
            Glide.with(this)
                    .load(recipe.getImageUrl())
                    .placeholder(R.drawable.placeholder_recipe)
                    .error(R.drawable.placeholder_recipe)
                    .into(ivRecipe);
        }
    }

    private void showDeleteConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Recipe")
                .setMessage("Are you sure you want to delete this recipe?")
                .setPositiveButton("Delete", (dialog, which) -> deleteRecipe())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteRecipe() {
        if (recipe != null && recipe.getId() != null) {
            firebaseManager.deleteRecipe(recipe.getId())
                    .addOnSuccessListener(aVoid -> {
                        Toast.makeText(this, "Recipe deleted successfully", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK); // Notify calling activity to refresh
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Failed to delete recipe", e);
                        Toast.makeText(this, "Failed to delete recipe", Toast.LENGTH_SHORT).show();
                    });
        }
    }
}