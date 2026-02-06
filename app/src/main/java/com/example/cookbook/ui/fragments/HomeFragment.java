package com.example.cookbook.ui.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cookbook.R;
import com.example.cookbook.api.model.AreaResponse;
import com.example.cookbook.api.model.CategoryResponse;
import com.example.cookbook.api.model.IngredientResponse;
import com.example.cookbook.model.Recipe;
import com.example.cookbook.model.RecipeFilter;
import com.example.cookbook.ui.activities.AddRecipeActivity;
import com.example.cookbook.ui.adapters.RecipeAdapter;
import com.example.cookbook.ui.dialog.RecipeFilterDialog;
import com.example.cookbook.util.FirebaseManager;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class HomeFragment extends Fragment implements RecipeFilterDialog.OnFilterAppliedListener {

    private static final String TAG = "HomeFragment";
    private FirebaseManager firebaseManager;
    private RecipeAdapter recipeAdapter;
    private List<Recipe> allRecipes = new ArrayList<>();

    // UI Components
    private RecyclerView recyclerView;
    private SearchView searchView;
    private ProgressBar progressBar;
    private LinearLayout emptyStateLayout;
    private FloatingActionButton fabAddRecipe;
    private ImageButton btnFilter, btnClearFilter;

    // Search & Filter State
    private RecipeFilter currentFilter = null;
    private String currentSearchQuery = "";

    // Data for Filter Dialog
    private ArrayList<CategoryResponse.Category> filterCategories = new ArrayList<>();
    private ArrayList<AreaResponse.Area> filterAreas = new ArrayList<>();
    private ArrayList<IngredientResponse.Ingredient> filterIngredients = new ArrayList<>();
    private boolean filterOptionsLoaded = false;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        firebaseManager = FirebaseManager.getInstance();

        // Initialize Views
        recyclerView = view.findViewById(R.id.recyclerView);
        searchView = view.findViewById(R.id.searchView);
        progressBar = view.findViewById(R.id.progressBar);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        fabAddRecipe = view.findViewById(R.id.fabAddRecipe);
        btnFilter = view.findViewById(R.id.btnFilter);
        btnClearFilter = view.findViewById(R.id.btnClearFilter);

        setupRecyclerView();
        setupSearchView();
        setupClickListeners();

        // Load initial data
        loadRecipes();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Refresh list when returning to the screen (e.g. after adding a recipe)
        loadRecipes();
    }

    private void setupRecyclerView() {
        // *** FIX: Added requireContext() as the first argument ***
        recipeAdapter = new RecipeAdapter(requireContext(), new ArrayList<>(),
                recipe -> {
                    // Click is handled inside the Adapter now
                },
                () -> {
                    // Reload list when a favorite status changes
                    loadRecipes();
                }
        );

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(recipeAdapter);
    }

    private void setupSearchView() {
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                currentSearchQuery = query;
                searchWithFilterOrQuery();
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                currentSearchQuery = newText;
                if (newText.length() > 2) {
                    searchWithFilterOrQuery();
                } else if (newText.isEmpty()) {
                    currentSearchQuery = "";
                    searchWithFilterOrQuery();
                }
                return true;
            }
        });

        // Handle the "X" button on search view
        searchView.setOnCloseListener(() -> {
            currentSearchQuery = "";
            currentFilter = null;
            btnClearFilter.setVisibility(View.GONE);
            loadRecipes(); // Reset to show user's recipes
            return false;
        });
    }

    private void setupClickListeners() {
        fabAddRecipe.setOnClickListener(v -> {
            Intent intent = new Intent(requireContext(), AddRecipeActivity.class);
            startActivityForResult(intent, 1001);
        });

        btnFilter.setOnClickListener(v -> showFilterDialogWithOptions());

        btnClearFilter.setOnClickListener(v -> clearFilter());
    }

    // --- Search & Filter Logic ---

    private void searchWithFilterOrQuery() {
        progressBar.setVisibility(View.VISIBLE);

        // If nothing is searched/filtered, show default user recipes
        if ((currentFilter == null || currentFilter.getValue() == null) && (currentSearchQuery == null || currentSearchQuery.isEmpty())) {
            loadRecipes();
            return;
        }

        List<Recipe> combinedResults = new ArrayList<>();
        final int[] completedSearches = {0};
        final int totalSearches = 2; // We search both Local DB and Online API

        // 1. Local Search (Firestore)
        performLocalSearch(combinedResults, completedSearches, totalSearches);

        // 2. API Search (Online)
        firebaseManager.searchOnlineRecipesByFilterOrQuery(currentFilter, currentSearchQuery, new FirebaseManager.OnRecipesLoadedListener() {
            @Override
            public void onRecipesLoaded(List<Recipe> apiRecipes) {
                combinedResults.addAll(apiRecipes);
                checkSearchCompletion(combinedResults, completedSearches, totalSearches);
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "API search failed: " + error);
                checkSearchCompletion(combinedResults, completedSearches, totalSearches);
            }
        });
    }

    private void performLocalSearch(List<Recipe> results, int[] completed, int total) {
        // Only search locally if we have a text query or specific filters supported locally
        if (currentSearchQuery != null && !currentSearchQuery.isEmpty()) {
            firebaseManager.searchRecipesByName(currentSearchQuery)
                    .addOnSuccessListener(queryDocumentSnapshots -> {
                        for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                            Recipe r = doc.toObject(Recipe.class);
                            r.setId(doc.getId());
                            if (!r.isImportedFromApi()) results.add(r);
                        }
                        checkSearchCompletion(results, completed, total);
                    })
                    .addOnFailureListener(e -> checkSearchCompletion(results, completed, total));
        } else if (currentFilter != null) {
            // Handle local filtering
            if (currentFilter.getType() == RecipeFilter.FilterType.CATEGORY) {
                firebaseManager.searchRecipesByCategory(currentFilter.getValue())
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                                Recipe r = doc.toObject(Recipe.class);
                                r.setId(doc.getId());
                                if (!r.isImportedFromApi()) results.add(r);
                            }
                            checkSearchCompletion(results, completed, total);
                        })
                        .addOnFailureListener(e -> checkSearchCompletion(results, completed, total));
            } else if (currentFilter.getType() == RecipeFilter.FilterType.INGREDIENT) {
                firebaseManager.searchRecipesByIngredient(currentFilter.getValue())
                        .addOnSuccessListener(queryDocumentSnapshots -> {
                            for (QueryDocumentSnapshot doc : queryDocumentSnapshots) {
                                Recipe r = doc.toObject(Recipe.class);
                                r.setId(doc.getId());
                                if (!r.isImportedFromApi()) results.add(r);
                            }
                            checkSearchCompletion(results, completed, total);
                        })
                        .addOnFailureListener(e -> checkSearchCompletion(results, completed, total));
            } else {
                // Area/Cuisine not supported locally
                checkSearchCompletion(results, completed, total);
            }
        } else {
            checkSearchCompletion(results, completed, total);
        }
    }

    private void checkSearchCompletion(List<Recipe> results, int[] completed, int total) {
        completed[0]++;
        if (completed[0] == total) {
            updateRecipeList(results);
            progressBar.setVisibility(View.GONE);
        }
    }

    private void updateRecipeList(List<Recipe> recipes) {
        List<Recipe> validRecipes = new ArrayList<>();

        // Filter out broken recipes
        for (Recipe r : recipes) {
            if (r != null && r.getTitle() != null && !r.getTitle().trim().isEmpty()) {
                validRecipes.add(r);
            }
        }

        recipeAdapter.updateRecipes(validRecipes);
        updateEmptyState(validRecipes.isEmpty());
    }

    // --- Data Loading ---

    private void loadRecipes() {
        progressBar.setVisibility(View.VISIBLE);
        firebaseManager.getUserRecipes()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    allRecipes.clear();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Recipe recipe = document.toObject(Recipe.class);
                        recipe.setId(document.getId());
                        allRecipes.add(recipe);
                    }

                    // By default, show user's recipes
                    List<Recipe> userRecipes = new ArrayList<>();
                    String currentUserId = firebaseManager.getCurrentUserId();

                    for (Recipe r : allRecipes) {
                        if (!r.isImportedFromApi() && r.getUserId() != null && r.getUserId().equals(currentUserId)) {
                            userRecipes.add(r);
                        }
                    }

                    updateRecipeList(userRecipes);
                    progressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(getContext(), "Failed to load recipes", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    updateEmptyState(true);
                });
    }

    // --- Filter Dialog Logic ---

    private void showFilterDialogWithOptions() {
        progressBar.setVisibility(View.VISIBLE);
        filterOptionsLoaded = false;
        filterCategories.clear();
        filterAreas.clear();
        filterIngredients.clear();

        // Load Categories
        firebaseManager.getCategories(new FirebaseManager.OnCategoriesLoadedListener() {
            @Override
            public void onCategoriesLoaded(List<CategoryResponse.Category> categories) {
                // Filter to show only specific categories
                List<String> allowed = Arrays.asList("Dessert", "Side", "Starter", "Breakfast", "Goat");
                for (CategoryResponse.Category cat : categories) {
                    if (allowed.contains(cat.getName())) filterCategories.add(cat);
                }
                checkAndShowDialog();
            }
            @Override
            public void onError(String error) { checkAndShowDialog(); }
        });

        // Load Areas
        firebaseManager.getAreas(new FirebaseManager.OnAreasLoadedListener() {
            @Override
            public void onAreasLoaded(List<AreaResponse.Area> areas) {
                filterAreas.addAll(areas);
                checkAndShowDialog();
            }
            @Override
            public void onError(String error) { checkAndShowDialog(); }
        });

        // Load Ingredients
        firebaseManager.getIngredients(new FirebaseManager.OnIngredientsLoadedListener() {
            @Override
            public void onIngredientsLoaded(List<IngredientResponse.Ingredient> ingredients) {
                filterIngredients.addAll(ingredients);
                checkAndShowDialog();
            }
            @Override
            public void onError(String error) { checkAndShowDialog(); }
        });
    }

    private void checkAndShowDialog() {
        // Only show dialog if we have data (or if some failed, we still try to show what we have)
        if (!filterCategories.isEmpty() && !filterAreas.isEmpty() && !filterIngredients.isEmpty() && !filterOptionsLoaded) {
            filterOptionsLoaded = true;
            progressBar.setVisibility(View.GONE);
            RecipeFilterDialog dialog = RecipeFilterDialog.newInstance(filterCategories, filterAreas, filterIngredients);
            dialog.show(getChildFragmentManager(), "filter_dialog");
        }
    }

    @Override
    public void onFilterApplied(RecipeFilter filter) {
        currentFilter = filter;
        btnClearFilter.setVisibility(View.VISIBLE);
        searchWithFilterOrQuery();
    }

    private void clearFilter() {
        currentFilter = null;
        currentSearchQuery = "";
        searchView.setQuery("", false);
        searchView.clearFocus();
        btnClearFilter.setVisibility(View.GONE);
        loadRecipes();
    }

    private void updateEmptyState(boolean isEmpty) {
        if (emptyStateLayout != null) {
            emptyStateLayout.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
            recyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1001 && resultCode == android.app.Activity.RESULT_OK) {
            loadRecipes();
        }
    }
}