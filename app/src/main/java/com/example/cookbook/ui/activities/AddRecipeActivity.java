package com.example.cookbook.ui.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.example.cookbook.R;
import com.example.cookbook.databinding.ActivityAddRecipeBinding;
import com.example.cookbook.model.Ingredient;
import com.example.cookbook.model.Recipe;
import com.example.cookbook.ui.adapters.IngredientAdapter;
import com.example.cookbook.util.FirebaseManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AddRecipeActivity extends AppCompatActivity implements IngredientAdapter.OnIngredientActionListener {

    private static final String TAG = "AddRecipeActivity";
    private static final int PICK_IMAGE_REQUEST = 1;

    private ActivityAddRecipeBinding binding;
    private FirebaseManager firebaseManager;
    private Uri selectedImageUri;
    private List<Ingredient> ingredients = new ArrayList<>();
    private Recipe editingRecipe = null;
    private boolean isEditMode = false;
    private IngredientAdapter ingredientAdapter;
    private int editingIngredientPosition = -1; // -1 means adding new, otherwise editing index

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddRecipeBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        // Setup Toolbar
        setSupportActionBar(binding.toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.title_add_recipe);
        }

        firebaseManager = FirebaseManager.getInstance();

        // Initialize Cloudinary - wrapped in try-catch in case it's already initialized
        try {
            Map<String, Object> config = new HashMap<>();
            config.put("cloud_name", "djrghjwsr");
            config.put("api_key", "764171749416285");
            config.put("api_secret", "CFgsjK88cHoqUCRoOLlXG9-glno");
            MediaManager.init(this, config);
        } catch (IllegalStateException e) {
            Log.w(TAG, "MediaManager already initialized");
        }

        setupSpinner();
        setupRecyclerView();
        setupClickListeners();

        // Check if we are in edit mode
        Intent intent = getIntent();
        isEditMode = intent.getBooleanExtra("edit_recipe", false);
        if (isEditMode && intent.hasExtra("recipe")) {
            editingRecipe = (Recipe) intent.getSerializableExtra("recipe");
            if (editingRecipe != null) {
                prefillFieldsForEdit(editingRecipe);
                getSupportActionBar().setTitle(R.string.title_edit_recipe);
            }
        }
    }

    private void setupSpinner() {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this,
                R.array.recipe_categories, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerCategory.setAdapter(adapter);
    }

    private void setupRecyclerView() {
        ingredientAdapter = new IngredientAdapter(ingredients, this);
        binding.rvIngredients.setLayoutManager(new LinearLayoutManager(this));
        binding.rvIngredients.setAdapter(ingredientAdapter);
    }

    private void setupClickListeners() {
        binding.btnAddIngredient.setOnClickListener(v -> addIngredient());
        binding.btnUploadImage.setOnClickListener(v -> selectImage());
        binding.btnSave.setOnClickListener(v -> saveRecipe());
    }

    private void addIngredient() {
        String name = binding.etIngredientName.getText().toString().trim();
        String amount = binding.etIngredientAmount.getText().toString().trim();
        String unit = binding.etIngredientUnit.getText().toString().trim();

        if (name.isEmpty() || amount.isEmpty()) {
            Toast.makeText(this, "Must enter name and amount", Toast.LENGTH_SHORT).show();
            return;
        }

        if (editingIngredientPosition >= 0) {
            // Update existing ingredient
            ingredients.set(editingIngredientPosition, new Ingredient(name, amount, unit));
            editingIngredientPosition = -1;
            binding.btnAddIngredient.setText(R.string.btn_add_ingredient);
        } else {
            // Add new ingredient
            ingredients.add(new Ingredient(name, amount, unit));
        }

        ingredientAdapter.updateIngredients(ingredients);
        clearIngredientInputs();
    }

    private void clearIngredientInputs() {
        binding.etIngredientName.setText("");
        binding.etIngredientAmount.setText("");
        binding.etIngredientUnit.setText("");
        binding.etIngredientName.requestFocus();
    }

    private void selectImage() {
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Image"), PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            selectedImageUri = data.getData();
            binding.ivRecipe.setImageURI(selectedImageUri);
            binding.ivRecipe.setVisibility(View.VISIBLE);
        }
    }

    private void prefillFieldsForEdit(Recipe recipe) {
        binding.etTitle.setText(recipe.getTitle());

        // Select the correct category in the spinner
        if (!TextUtils.isEmpty(recipe.getCategory())) {
            ArrayAdapter<CharSequence> adapter = (ArrayAdapter<CharSequence>) binding.spinnerCategory.getAdapter();
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getItem(i).toString().equals(recipe.getCategory())) {
                    binding.spinnerCategory.setSelection(i);
                    break;
                }
            }
        }

        binding.etInstructions.setText(recipe.getInstructions());

        // Copy the list to avoid overwriting the original before saving
        ingredients = new ArrayList<>(recipe.getIngredients());
        ingredientAdapter.updateIngredients(ingredients);

        if (!TextUtils.isEmpty(recipe.getImageUrl())) {
            binding.ivRecipe.setVisibility(View.VISIBLE);
            Glide.with(this)
                    .load(recipe.getImageUrl())
                    .placeholder(R.drawable.placeholder_recipe)
                    .into(binding.ivRecipe);
        }
    }

    private void saveRecipe() {
        String title = binding.etTitle.getText().toString().trim();
        String instructions = binding.etInstructions.getText().toString().trim();

        if (title.isEmpty() || instructions.isEmpty() || ingredients.isEmpty()) {
            Toast.makeText(this, "Please fill all fields and add ingredients", Toast.LENGTH_SHORT).show();
            return;
        }

        binding.progressBar.setVisibility(View.VISIBLE);
        binding.btnSave.setEnabled(false); // Prevent double clicking

        if (selectedImageUri != null) {
            // New image selected - upload to cloud
            uploadImageToCloudinary(selectedImageUri);
        } else if (isEditMode && editingRecipe != null) {
            // Edit mode without image change - save with existing URL
            saveDataToFirestore(editingRecipe.getImageUrl());
        } else {
            // New recipe without image
            saveDataToFirestore("");
        }
    }

    // Upload image to cloud and then save data to Firebase
    private void uploadImageToCloudinary(Uri imageUri) {
        Log.d(TAG, "Starting image upload...");
        MediaManager.get().upload(imageUri)
                .callback(new UploadCallback() {
                    @Override
                    public void onSuccess(String requestId, Map resultData) {
                        String imageUrl = (String) resultData.get("secure_url");
                        Log.d(TAG, "Image uploaded: " + imageUrl);
                        saveDataToFirestore(imageUrl);
                    }

                    @Override
                    public void onError(String requestId, ErrorInfo error) {
                        runOnUiThread(() -> {
                            binding.progressBar.setVisibility(View.GONE);
                            binding.btnSave.setEnabled(true);
                            Toast.makeText(AddRecipeActivity.this, "Image upload failed", Toast.LENGTH_SHORT).show();
                            Log.e(TAG, "Cloudinary error: " + error.getDescription());
                        });
                    }

                    @Override
                    public void onStart(String requestId) { }
                    @Override
                    public void onProgress(String requestId, long bytes, long totalBytes) { }
                    @Override
                    public void onReschedule(String requestId, ErrorInfo error) { }
                }).dispatch();
    }

    // Final function to save data to DB
    private void saveDataToFirestore(String imageUrl) {
        String title = binding.etTitle.getText().toString();
        String category = binding.spinnerCategory.getSelectedItem().toString();
        String instructions = binding.etInstructions.getText().toString();

        // Create recipe object
        String recipeId = (isEditMode && editingRecipe != null) ? editingRecipe.getId() : null;

        Recipe recipe = new Recipe(
                recipeId,
                title,
                category,
                ingredients,
                instructions,
                imageUrl,
                firebaseManager.getCurrentUserId()
        );

        if (isEditMode && recipeId != null) {
            // Update existing recipe
            firebaseManager.updateRecipe(recipe).addOnCompleteListener(task -> {
                binding.progressBar.setVisibility(View.GONE);
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Recipe updated!", Toast.LENGTH_SHORT).show();

                    // Return result to previous screen to refresh immediately
                    Intent resultIntent = new Intent();
                    resultIntent.putExtra("updated_recipe", recipe);
                    setResult(RESULT_OK, resultIntent);
                    finish();
                } else {
                    binding.btnSave.setEnabled(true);
                    Toast.makeText(this, "Update failed: " + task.getException().getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } else {
            // Create new recipe
            firebaseManager.addRecipe(recipe, task -> {
                binding.progressBar.setVisibility(View.GONE);
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Recipe saved!", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                } else {
                    binding.btnSave.setEnabled(true);
                    Toast.makeText(this, "Save failed", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onEditIngredient(Ingredient ingredient, int position) {
        binding.etIngredientName.setText(ingredient.getName());
        binding.etIngredientAmount.setText(ingredient.getAmount());
        binding.etIngredientUnit.setText(ingredient.getUnit());

        editingIngredientPosition = position;
        binding.btnAddIngredient.setText("Update"); // Short and clear
        binding.etIngredientName.requestFocus();
    }

    @Override
    public void onDeleteIngredient(int position) {
        ingredients.remove(position);
        ingredientAdapter.updateIngredients(ingredients);

        // If we deleted what we were currently editing - reset state
        if (editingIngredientPosition == position) {
            editingIngredientPosition = -1;
            binding.btnAddIngredient.setText(R.string.btn_add_ingredient);
            clearIngredientInputs();
        }
    }
}