package com.example.cookbook.ui.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.cookbook.R;
import com.example.cookbook.model.Ingredient;

import java.util.List;

public class IngredientAdapter extends RecyclerView.Adapter<IngredientAdapter.IngredientViewHolder> {

    private List<Ingredient> ingredients;
    private final OnIngredientActionListener listener;

    // Interface to handle clicks on Edit/Delete buttons
    public interface OnIngredientActionListener {
        void onEditIngredient(Ingredient ingredient, int position);
        void onDeleteIngredient(int position);
    }

    public IngredientAdapter(List<Ingredient> ingredients, OnIngredientActionListener listener) {
        this.ingredients = ingredients;
        this.listener = listener;
    }

    @NonNull
    @Override
    public IngredientViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the layout for a single row
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ingredient, parent, false);
        return new IngredientViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull IngredientViewHolder holder, int position) {
        holder.bind(ingredients.get(position), position);
    }

    @Override
    public int getItemCount() {
        return ingredients.size();
    }

    // Helper method to update the list
    public void updateIngredients(List<Ingredient> newIngredients) {
        this.ingredients = newIngredients;
        notifyDataSetChanged();
    }

    public List<Ingredient> getIngredients() {
        return ingredients;
    }

    // ViewHolder class to hold references to the UI elements
    class IngredientViewHolder extends RecyclerView.ViewHolder {

        TextView tvName, tvAmount;
        ImageButton btnEdit, btnDelete;

        IngredientViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvIngredientName);
            tvAmount = itemView.findViewById(R.id.tvIngredientAmount);
            btnEdit = itemView.findViewById(R.id.btnEditIngredient);
            btnDelete = itemView.findViewById(R.id.btnDeleteIngredient);
        }

        void bind(final Ingredient ingredient, final int position) {
            tvName.setText(ingredient.getName());
            tvAmount.setText(ingredient.getAmount() + " " + ingredient.getUnit());

            // Handle edit click
            btnEdit.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onEditIngredient(ingredient, position);
                }
            });

            // Handle delete click
            btnDelete.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onDeleteIngredient(position);
                }
            });
        }
    }
}