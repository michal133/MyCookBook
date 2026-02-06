package com.example.cookbook.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cookbook.R;
import com.example.cookbook.model.Recipe;
import com.example.cookbook.ui.adapters.RecipeAdapter;
import com.example.cookbook.util.FirebaseManager;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FavoritesFragment extends Fragment {

    private FirebaseManager firebaseManager;
    private RecipeAdapter recipeAdapter;

    // UI Components
    private RecyclerView recyclerView;
    private LinearLayout emptyStateLayout;
    private ProgressBar progressBar;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_favorites, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Initialize UI components
        recyclerView = view.findViewById(R.id.recyclerView);
        emptyStateLayout = view.findViewById(R.id.emptyStateLayout);
        progressBar = view.findViewById(R.id.progressBar);

        firebaseManager = FirebaseManager.getInstance();

        setupRecyclerView();
        loadFavoriteRecipes();
    }

    @Override
    public void onResume() {
        super.onResume();
        // Reload list when returning to this screen
        loadFavoriteRecipes();
    }

    private void setupRecyclerView() {
        // Initialize adapter with Context (requireContext()) + Listeners
        recipeAdapter = new RecipeAdapter(
                requireContext(),
                new ArrayList<>(),

                // Click listener (open recipe) - handled inside adapter now, but keeping for safety
                recipe -> {
                    // Logic is mainly in the adapter, this can be empty or used for tracking
                },

                // Favorite changed listener (refresh list)
                () -> loadFavoriteRecipes()
        );

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setAdapter(recipeAdapter);
    }

    private void loadFavoriteRecipes() {
        progressBar.setVisibility(View.VISIBLE);

        firebaseManager.getFavoriteRecipes()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Recipe> recipes = new ArrayList<>();
                    for (QueryDocumentSnapshot document : queryDocumentSnapshots) {
                        Recipe recipe = document.toObject(Recipe.class);
                        recipe.setId(document.getId());
                        recipes.add(recipe);
                    }

                    recipeAdapter.updateRecipes(recipes);
                    updateEmptyState(recipes.isEmpty());
                    progressBar.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    Toast.makeText(requireContext(), "Error loading favorites", Toast.LENGTH_SHORT).show();
                    progressBar.setVisibility(View.GONE);
                    updateEmptyState(true);
                });
    }

    // Toggle between list and "No favorites" message
    private void updateEmptyState(boolean isEmpty) {
        if (isEmpty) {
            emptyStateLayout.setVisibility(View.VISIBLE);
            recyclerView.setVisibility(View.GONE);
        } else {
            emptyStateLayout.setVisibility(View.GONE);
            recyclerView.setVisibility(View.VISIBLE);
        }
    }
}