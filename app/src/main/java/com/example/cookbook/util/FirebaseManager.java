package com.example.cookbook.util;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.example.cookbook.CookBookApplication;
import com.example.cookbook.api.ApiClient;
import com.example.cookbook.api.model.ApiRecipe;
import com.example.cookbook.api.model.ApiRecipeResponse;
import com.example.cookbook.api.model.AreaResponse;
import com.example.cookbook.api.model.CategoryResponse;
import com.example.cookbook.api.model.IngredientResponse;
import com.example.cookbook.model.Ingredient;
import com.example.cookbook.model.Recipe;
import com.example.cookbook.model.RecipeFilter;
import com.example.cookbook.model.User;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FirebaseManager {
    private static final String TAG = "FirebaseManager";
    private static final String USERS_COLLECTION = "users";
    private static final String RECIPES_COLLECTION = "recipes";

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private final Context context;

    private static FirebaseManager instance;

    // Singleton Constructor
    private FirebaseManager() {
        try {
            auth = FirebaseAuth.getInstance();
            db = FirebaseFirestore.getInstance();
            context = CookBookApplication.getInstance();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing Firebase services", e);
            throw e;
        }
    }

    public static synchronized FirebaseManager getInstance() {
        if (instance == null) {
            instance = new FirebaseManager();
        }
        return instance;
    }

    // --- Authentication Methods ---

    public Task<AuthResult> registerUser(String email, String password) {
        return auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    // Create user document in Firestore upon registration
                    if (authResult.getUser() != null) {
                        FirebaseUser firebaseUser = authResult.getUser();
                        User newUser = new User(firebaseUser.getUid(), email);
                        db.collection(USERS_COLLECTION)
                                .document(firebaseUser.getUid())
                                .set(newUser)
                                .addOnFailureListener(e -> Log.e(TAG, "Error creating user doc", e));
                    }
                })
                .continueWithTask(task -> {
                    if (task.isSuccessful()) {
                        return task;
                    } else {
                        // Return user-friendly error
                        String errorMessage = translateRegistrationError(task.getException().getMessage());
                        return Tasks.forException(new Exception(errorMessage));
                    }
                });
    }

    public Task<AuthResult> loginUser(String email, String password) {
        return auth.signInWithEmailAndPassword(email, password)
                .continueWithTask(task -> {
                    if (task.isSuccessful()) {
                        return task;
                    } else {
                        String errorMessage = translateFirebaseError(task.getException().getMessage());
                        return Tasks.forException(new Exception(errorMessage));
                    }
                });
    }

    public void logoutUser() {
        auth.signOut();
    }

    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public FirebaseUser getCurrentUser() {
        return auth.getCurrentUser();
    }

    public Task<Void> sendPasswordResetEmail(String email) {
        return auth.sendPasswordResetEmail(email)
                .continueWithTask(task -> {
                    if (task.isSuccessful()) {
                        return task;
                    } else {
                        String errorMessage = translatePasswordResetError(task.getException().getMessage());
                        return Tasks.forException(new Exception(errorMessage));
                    }
                });
    }

    // --- Recipe Methods (Firestore) ---

    // Internal use: Returns Task (used by favoriteApiRecipe)
    public Task<DocumentReference> addRecipe(Recipe recipe) {
        String userId = getCurrentUserId();
        if (userId != null) {
            recipe.setUserId(userId);
        }
        return db.collection(RECIPES_COLLECTION).add(recipe);
    }

    // External use: Accepts a listener (used by Activities)
    public void addRecipe(Recipe recipe, com.google.android.gms.tasks.OnCompleteListener<com.google.firebase.firestore.DocumentReference> listener) {
        String userId = getCurrentUserId();
        if (userId == null) return;

        recipe.setUserId(userId);
        db.collection(RECIPES_COLLECTION)
                .add(recipe)
                .addOnCompleteListener(listener);
    }

    public Task<Void> updateRecipe(Recipe recipe) {
        if (recipe.getId() == null) {
            return Tasks.forException(new Exception("Recipe ID is missing"));
        }
        return db.collection(RECIPES_COLLECTION)
                .document(recipe.getId())
                .set(recipe);
    }

    public Task<Void> deleteRecipe(String recipeId) {
        return db.collection(RECIPES_COLLECTION)
                .document(recipeId)
                .delete();
    }

    public Task<QuerySnapshot> getUserRecipes() {
        String userId = getCurrentUserId();
        if (userId == null) {
            return Tasks.forException(new Exception("User not logged in"));
        }
        return db.collection(RECIPES_COLLECTION)
                .whereEqualTo("userId", userId)
                .get();
    }

    public Task<QuerySnapshot> searchRecipesByName(String query) {
        String userId = getCurrentUserId();
        if (userId == null) return Tasks.forException(new Exception("Not logged in"));

        String searchQuery = query.toLowerCase();
        // Simple search logic
        return db.collection(RECIPES_COLLECTION)
                .whereEqualTo("userId", userId)
                .whereGreaterThanOrEqualTo("title", searchQuery)
                .whereLessThanOrEqualTo("title", searchQuery + "\uf8ff")
                .get();
    }

    public Task<QuerySnapshot> searchRecipesByCategory(String category) {
        return db.collection(RECIPES_COLLECTION)
                .whereEqualTo("userId", getCurrentUserId())
                .whereEqualTo("category", category)
                .get();
    }

    public Task<QuerySnapshot> searchRecipesByIngredient(String ingredient) {
        return db.collection(RECIPES_COLLECTION)
                .whereEqualTo("userId", getCurrentUserId())
                .whereArrayContains("ingredients", ingredient)
                .get();
    }

    // --- Favorites ---

    public Task<Void> toggleFavoriteRecipe(String recipeId, boolean isFavorite) {
        return db.collection(RECIPES_COLLECTION)
                .document(recipeId)
                .update("favorite", isFavorite);
    }

    public Task<QuerySnapshot> getFavoriteRecipes() {
        return db.collection(RECIPES_COLLECTION)
                .whereEqualTo("userId", getCurrentUserId())
                .whereEqualTo("favorite", true)
                .get();
    }

    // Save an API recipe to Firestore as a favorite
    public Task<Void> favoriteApiRecipe(Recipe recipe) {
        try {
            recipe.setImportedFromApi(true);
            recipe.setFavorite(true);
            return addRecipe(recipe)
                    .continueWithTask(task -> {
                        if (task.isSuccessful()) {
                            String recipeId = task.getResult().getId();
                            recipe.setId(recipeId);
                            // Update the ID inside the document
                            return task.getResult().getParent().document(recipeId)
                                    .update("favorite", true);
                        } else {
                            throw task.getException();
                        }
                    });
        } catch (Exception e) {
            return Tasks.forException(e);
        }
    }

    // --- External API (TheMealDB) Integration ---

    public void searchOnlineRecipesByFilterOrQuery(RecipeFilter filter, String query, OnRecipesLoadedListener listener) {
        // If query exists, search by query
        if (query != null && !query.isEmpty()) {
            searchOnlineRecipes(query, listener);
            return;
        }
        // If filter exists, search by filter
        if (filter != null && filter.getValue() != null && !filter.getValue().isEmpty()) {
            searchOnlineRecipesWithFilter(filter, listener);
            return;
        }
        listener.onRecipesLoaded(new ArrayList<>());
    }

    public void searchOnlineRecipes(String query, OnRecipesLoadedListener listener) {
        searchOnlineRecipesWithFilter(RecipeFilter.bySearch(query), listener);
    }

    public void searchOnlineRecipesWithFilter(RecipeFilter filter, OnRecipesLoadedListener listener) {
        Call<ApiRecipeResponse> call;

        switch (filter.getType()) {
            case CATEGORY:
                call = ApiClient.getRecipeService().filterByCategory(filter.getValue());
                break;
            case AREA:
                call = ApiClient.getRecipeService().filterByArea(filter.getValue());
                break;
            case INGREDIENT:
                call = ApiClient.getRecipeService().filterByIngredient(filter.getValue());
                break;
            case SEARCH:
            default:
                call = ApiClient.getRecipeService().searchRecipes(filter.getValue());
                break;
        }

        call.enqueue(new Callback<ApiRecipeResponse>() {
            @Override
            public void onResponse(Call<ApiRecipeResponse> call, Response<ApiRecipeResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ApiRecipe> results = response.body().getResults();
                    if (results != null) {
                        List<Recipe> recipes = convertApiRecipesToLocalRecipes(results);
                        // Limit to 10 results
                        if (recipes.size() > 10) recipes = recipes.subList(0, 10);
                        listener.onRecipesLoaded(recipes);
                    } else {
                        listener.onRecipesLoaded(new ArrayList<>());
                    }
                } else {
                    listener.onError("Failed to load recipes");
                }
            }

            @Override
            public void onFailure(Call<ApiRecipeResponse> call, Throwable t) {
                listener.onError(t.getMessage());
            }
        });
    }

    public void fetchFullRecipeById(String id, OnRecipesLoadedListener listener) {
        ApiClient.getRecipeService().getRecipeInformation(id).enqueue(new Callback<ApiRecipeResponse>() {
            @Override
            public void onResponse(Call<ApiRecipeResponse> call, Response<ApiRecipeResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().getResults() != null) {
                    List<Recipe> recipes = convertApiRecipesToLocalRecipes(response.body().getResults());
                    listener.onRecipesLoaded(recipes);
                } else {
                    listener.onError("Recipe details not found");
                }
            }
            @Override
            public void onFailure(Call<ApiRecipeResponse> call, Throwable t) {
                listener.onError(t.getMessage());
            }
        });
    }

    // --- API Loading Callbacks ---

    public void getCategories(OnCategoriesLoadedListener listener) {
        ApiClient.getRecipeService().getCategories().enqueue(new Callback<CategoryResponse>() {
            @Override
            public void onResponse(Call<CategoryResponse> call, Response<CategoryResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    listener.onCategoriesLoaded(response.body().getCategories());
                } else {
                    listener.onError("Failed");
                }
            }
            @Override
            public void onFailure(Call<CategoryResponse> call, Throwable t) {
                listener.onError(t.getMessage());
            }
        });
    }

    public void getAreas(OnAreasLoadedListener listener) {
        ApiClient.getRecipeService().getAreas("list").enqueue(new Callback<AreaResponse>() {
            @Override
            public void onResponse(Call<AreaResponse> call, Response<AreaResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    listener.onAreasLoaded(response.body().getAreas());
                } else {
                    listener.onError("Failed");
                }
            }
            @Override
            public void onFailure(Call<AreaResponse> call, Throwable t) {
                listener.onError(t.getMessage());
            }
        });
    }

    public void getIngredients(OnIngredientsLoadedListener listener) {
        ApiClient.getRecipeService().getIngredients("list").enqueue(new Callback<IngredientResponse>() {
            @Override
            public void onResponse(Call<IngredientResponse> call, Response<IngredientResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    listener.onIngredientsLoaded(response.body().getIngredients());
                } else {
                    listener.onError("Failed");
                }
            }
            @Override
            public void onFailure(Call<IngredientResponse> call, Throwable t) {
                listener.onError(t.getMessage());
            }
        });
    }

    // --- Helper Methods ---

    private List<Recipe> convertApiRecipesToLocalRecipes(List<ApiRecipe> apiRecipes) {
        List<Recipe> recipes = new ArrayList<>();
        for (ApiRecipe api : apiRecipes) {
            try {
                Recipe r = new Recipe();
                r.setId(api.getId());
                r.setTitle(api.getTitle());
                r.setInstructions(api.getInstructions());
                r.setImageUrl(api.getImageUrl());
                r.setCategory(api.getCategory() != null ? api.getCategory() : "Other");
                r.setImportedFromApi(true);
                r.setFavorite(false);
                r.setIngredients(extractIngredientsFromTheMealDB(api));
                recipes.add(r);
            } catch (Exception e) {
                Log.e(TAG, "Conversion error", e);
            }
        }
        return recipes;
    }

    private List<Ingredient> extractIngredientsFromTheMealDB(ApiRecipe api) {
        List<Ingredient> ingredients = new ArrayList<>();
        // Helper arrays to iterate through the 20 possible ingredients
        String[] ing = {api.getIngredient1(), api.getIngredient2(), api.getIngredient3(), api.getIngredient4(), api.getIngredient5(),
                api.getIngredient6(), api.getIngredient7(), api.getIngredient8(), api.getIngredient9(), api.getIngredient10(),
                api.getIngredient11(), api.getIngredient12(), api.getIngredient13(), api.getIngredient14(), api.getIngredient15(),
                api.getIngredient16(), api.getIngredient17(), api.getIngredient18(), api.getIngredient19(), api.getIngredient20()};

        String[] meas = {api.getMeasure1(), api.getMeasure2(), api.getMeasure3(), api.getMeasure4(), api.getMeasure5(),
                api.getMeasure6(), api.getMeasure7(), api.getMeasure8(), api.getMeasure9(), api.getMeasure10(),
                api.getMeasure11(), api.getMeasure12(), api.getMeasure13(), api.getMeasure14(), api.getMeasure15(),
                api.getMeasure16(), api.getMeasure17(), api.getMeasure18(), api.getMeasure19(), api.getMeasure20()};

        for (int i = 0; i < ing.length; i++) {
            if (ing[i] != null && !ing[i].trim().isEmpty()) {
                String measure = (meas[i] != null && !meas[i].trim().isEmpty()) ? meas[i].trim() : "1";
                ingredients.add(new Ingredient(ing[i].trim(), measure, ""));
            }
        }

        if (ingredients.isEmpty()) {
            ingredients.add(new Ingredient("Main Item", "1", "serving"));
        }
        return ingredients;
    }

    // --- Error Translation ---

    private String translateRegistrationError(String error) {
        if (error == null) return "Registration failed";
        if (error.contains("already in use")) return "Email already registered";
        if (error.contains("badly formatted")) return "Invalid email";
        if (error.contains("weak")) return "Password too weak";
        return "Registration error";
    }

    private String translateFirebaseError(String error) {
        if (error == null) return "Action failed";
        if (error.contains("no user record") || error.contains("not found")) return "Account not found";
        if (error.contains("password is invalid") || error.contains("credential")) return "Invalid email or password";
        if (error.contains("network")) return "Network error";
        return "Error occurred";
    }

    private String translatePasswordResetError(String error) {
        if (error == null) return "Failed";
        if (error.contains("no user")) return "Email not found";
        if (error.contains("badly formatted")) return "Invalid email";
        return "Error sending email";
    }

    // --- Interfaces ---

    public interface OnRecipesLoadedListener {
        void onRecipesLoaded(List<Recipe> recipes);
        void onError(String error);
    }

    public interface OnCategoriesLoadedListener {
        void onCategoriesLoaded(List<CategoryResponse.Category> categories);
        void onError(String error);
    }

    public interface OnAreasLoadedListener {
        void onAreasLoaded(List<AreaResponse.Area> areas);
        void onError(String error);
    }

    public interface OnIngredientsLoadedListener {
        void onIngredientsLoaded(List<IngredientResponse.Ingredient> ingredients);
        void onError(String error);
    }
}