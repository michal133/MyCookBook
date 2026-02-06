package com.example.cookbook.ui.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.cookbook.R;
import com.example.cookbook.model.Recipe;
import com.example.cookbook.ui.activities.RecipeDetailActivity;
import com.example.cookbook.util.FirebaseManager;

import java.util.List;

public class RecipeAdapter extends RecyclerView.Adapter<RecipeAdapter.RecipeViewHolder> {

    private List<Recipe> recipes;
    private Context context; // Useful for Toasts and Intents
    private final OnRecipeClickListener listener;
    private final FirebaseManager firebaseManager;
    private final OnFavoriteChangedListener favoriteChangedListener;

    // Interface to notify when favorite status changes
    public interface OnFavoriteChangedListener {
        void onFavoriteChanged();
    }

    // Interface to handle recipe clicks
    public interface OnRecipeClickListener {
        void onRecipeClick(Recipe recipe);
    }

    public RecipeAdapter(Context context, List<Recipe> recipes, OnRecipeClickListener listener, OnFavoriteChangedListener favoriteChangedListener) {
        this.context = context;
        this.recipes = recipes;
        this.listener = listener;
        this.firebaseManager = FirebaseManager.getInstance();
        this.favoriteChangedListener = favoriteChangedListener;
    }

    @NonNull
    @Override
    public RecipeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_recipe, parent, false);
        return new RecipeViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecipeViewHolder holder, int position) {
        holder.bind(recipes.get(position));
    }

    @Override
    public int getItemCount() {
        return recipes.size();
    }

    public void updateRecipes(List<Recipe> newRecipes) {
        this.recipes = newRecipes;
        notifyDataSetChanged();
    }

    class RecipeViewHolder extends RecyclerView.ViewHolder {

        TextView tvTitle, tvCategory, tvIngredients;
        ImageView ivRecipe, ivFavorite, ivShare;

        RecipeViewHolder(@NonNull View itemView) {
            super(itemView);
            // Initialize Views
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvCategory = itemView.findViewById(R.id.tvCategory);
            tvIngredients = itemView.findViewById(R.id.tvIngredients);
            ivRecipe = itemView.findViewById(R.id.ivRecipe);
            ivFavorite = itemView.findViewById(R.id.ivFavorite);
            ivShare = itemView.findViewById(R.id.ivShare);
        }

        void bind(final Recipe recipe) {
            tvTitle.setText(recipe.getTitle());
            tvCategory.setText(recipe.getCategory());

            int ingredientCount = (recipe.getIngredients() != null) ? recipe.getIngredients().size() : 0;
            tvIngredients.setText(ingredientCount + " ingredients");

            // Load image
            if (recipe.getImageUrl() != null && !recipe.getImageUrl().isEmpty()) {
                Glide.with(context)
                        .load(recipe.getImageUrl())
                        .placeholder(R.drawable.placeholder_recipe)
                        .error(R.drawable.placeholder_recipe)
                        .into(ivRecipe);
            } else {
                ivRecipe.setImageResource(R.drawable.placeholder_recipe);
            }

            // Update favorite icon
            updateFavoriteIcon(recipe.isFavorite());

            // --- Click Listeners ---

            // 1. Item Click (Open Details)
            itemView.setOnClickListener(v -> {
                // If it's an API recipe with missing details, fetch them first
                boolean missingDetails = recipe.isImportedFromApi() &&
                        (recipe.getInstructions() == null || recipe.getInstructions().length() < 10);

                if (missingDetails) {
                    fetchAndOpenRecipe(recipe);
                } else {
                    openDetailActivity(recipe);
                }
            });

            // 2. Favorite Click
            ivFavorite.setOnClickListener(v -> handleFavoriteClick(recipe));

            // 3. Share Click
            ivShare.setOnClickListener(v -> shareRecipe(recipe));
        }

        private void updateFavoriteIcon(boolean isFavorite) {
            if (isFavorite) {
                ivFavorite.setImageResource(R.drawable.ic_favorite_alt_filled);
            } else {
                ivFavorite.setImageResource(R.drawable.ic_favorite_border);
            }
        }

        private void fetchAndOpenRecipe(Recipe recipe) {
            if (recipe.getId() == null) {
                Toast.makeText(context, "Error: Missing ID", Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(context, "Loading full recipe...", Toast.LENGTH_SHORT).show();

            firebaseManager.fetchFullRecipeById(recipe.getId(), new FirebaseManager.OnRecipesLoadedListener() {
                @Override
                public void onRecipesLoaded(List<Recipe> recipes) {
                    if (recipes != null && !recipes.isEmpty()) {
                        openDetailActivity(recipes.get(0));
                    } else {
                        Toast.makeText(context, "Recipe details not found", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onError(String error) {
                    Toast.makeText(context, "Failed to load details", Toast.LENGTH_SHORT).show();
                }
            });
        }

        private void openDetailActivity(Recipe recipe) {
            Intent intent = new Intent(context, RecipeDetailActivity.class);
            intent.putExtra("recipe", recipe);
            context.startActivity(intent);
        }

        private void handleFavoriteClick(Recipe recipe) {
            boolean newState = !recipe.isFavorite();
            recipe.setFavorite(newState);
            updateFavoriteIcon(newState);

            if (recipe.isImportedFromApi() && newState) {
                // Save API recipe to favorites
                firebaseManager.favoriteApiRecipe(recipe)
                        .addOnSuccessListener(aVoid -> {
                            Toast.makeText(context, "Added to favorites", Toast.LENGTH_SHORT).show();
                            if (favoriteChangedListener != null) favoriteChangedListener.onFavoriteChanged();
                        })
                        .addOnFailureListener(e -> {
                            // Revert UI on failure
                            recipe.setFavorite(!newState);
                            updateFavoriteIcon(!newState);
                            Toast.makeText(context, "Failed to save favorite", Toast.LENGTH_SHORT).show();
                        });
            } else {
                // Toggle local favorite
                firebaseManager.toggleFavoriteRecipe(recipe.getId(), newState)
                        .addOnSuccessListener(aVoid -> {
                            String msg = newState ? "Added to favorites" : "Removed from favorites";
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();

                            // Check if we need to remove it from the list
                            if (!newState && recipe.isImportedFromApi()) {
                                firebaseManager.deleteRecipe(recipe.getId());
                            }
                            if (favoriteChangedListener != null) favoriteChangedListener.onFavoriteChanged();
                        })
                        .addOnFailureListener(e -> {
                            recipe.setFavorite(!newState);
                            updateFavoriteIcon(!newState);
                            Toast.makeText(context, "Error updating favorite", Toast.LENGTH_SHORT).show();
                        });
            }
        }

        private void shareRecipe(Recipe recipe) {
            StringBuilder shareText = new StringBuilder();
            shareText.append("Check out this recipe!\n\n")
                    .append(recipe.getTitle()).append("\n")
                    .append("Category: ").append(recipe.getCategory()).append("\n\n")
                    .append("Ingredients:\n");

            if (recipe.getIngredients() != null) {
                for (int i = 0; i < recipe.getIngredients().size(); i++) {
                    shareText.append("- ").append(recipe.getIngredients().get(i).getName())
                            .append(" (").append(recipe.getIngredients().get(i).getAmount())
                            .append(" ").append(recipe.getIngredients().get(i).getUnit()).append(")\n");
                }
            }

            shareText.append("\nInstructions:\n").append(recipe.getInstructions());

            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText.toString());

            try {
                context.startActivity(Intent.createChooser(shareIntent, "Share Recipe"));
            } catch (Exception e) {
                Toast.makeText(context, "No app found to share", Toast.LENGTH_SHORT).show();
            }
        }
    }
}