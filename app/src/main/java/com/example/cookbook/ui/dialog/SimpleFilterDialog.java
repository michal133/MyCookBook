package com.example.cookbook.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.cookbook.R;
import com.example.cookbook.api.model.AreaResponse;
import com.example.cookbook.api.model.IngredientResponse;
import com.example.cookbook.util.FirebaseManager;

import java.util.ArrayList;
import java.util.List;

public class SimpleFilterDialog extends DialogFragment {

    // Enum to define which type of filter this dialog handles
    public enum FilterType {
        CUISINE,
        INGREDIENT,
        DIETARY
    }

    private FirebaseManager firebaseManager;
    private OnFilterSelectedListener listener;
    private FilterType filterType;
    private Spinner spinner;
    private Button btnApply;
    private List<String> filterOptions = new ArrayList<>(); // Stores the list of options to show in spinner
    private boolean dataLoaded = false;

    // Interface to communicate back to the Activity/Fragment
    public interface OnFilterSelectedListener {
        void onFilterSelected(FilterType type, String value);
    }

    // Factory method to create a new instance
    public static SimpleFilterDialog newInstance(FilterType type) {
        SimpleFilterDialog dialog = new SimpleFilterDialog();
        dialog.filterType = type;
        return dialog;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Verify that the host activity implements the callback interface
        if (context instanceof OnFilterSelectedListener) {
            listener = (OnFilterSelectedListener) context;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        firebaseManager = FirebaseManager.getInstance();

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_simple_filter, null);

        setupViews(view);
        loadFilterOptions();

        builder.setView(view);
        return builder.create();
    }

    private void setupViews(View view) {
        TextView tvTitle = view.findViewById(R.id.tvTitle);
        spinner = view.findViewById(R.id.spinner);
        btnApply = view.findViewById(R.id.btnApply);
        Button btnCancel = view.findViewById(R.id.btnCancel);

        // Set dynamic title based on filter type
        if (filterType != null) {
            switch (filterType) {
                case CUISINE:
                    tvTitle.setText("Select Cuisine");
                    break;
                case INGREDIENT:
                    tvTitle.setText("Select Main Ingredient");
                    break;
                case DIETARY:
                    tvTitle.setText("Select Dietary Restriction");
                    break;
            }
        }

        btnApply.setOnClickListener(v -> applyFilter());

        btnCancel.setOnClickListener(v -> dismiss());

        // Disable button until data loads
        btnApply.setEnabled(false);
    }

    private void loadFilterOptions() {
        if (filterType == null) return;

        switch (filterType) {
            case CUISINE:
                loadCuisines();
                break;
            case INGREDIENT:
                loadIngredients();
                break;
            case DIETARY:
                loadDietaryOptions();
                break;
        }
    }

    private void loadCuisines() {
        firebaseManager.getAreas(new FirebaseManager.OnAreasLoadedListener() {
            @Override
            public void onAreasLoaded(List<AreaResponse.Area> areas) {
                filterOptions.clear();
                for (AreaResponse.Area area : areas) {
                    filterOptions.add(area.getName());
                }
                setupSpinner();
            }

            @Override
            public void onError(String error) {
                // Fallback hardcoded list if API fails
                String[] fallbackCuisines = {
                        "American", "British", "Chinese", "French", "Indian",
                        "Italian", "Japanese", "Mexican", "Spanish", "Thai"
                };
                useFallbackOptions(fallbackCuisines);
            }
        });
    }

    private void loadIngredients() {
        firebaseManager.getIngredients(new FirebaseManager.OnIngredientsLoadedListener() {
            @Override
            public void onIngredientsLoaded(List<IngredientResponse.Ingredient> ingredients) {
                filterOptions.clear();
                for (IngredientResponse.Ingredient ingredient : ingredients) {
                    filterOptions.add(ingredient.getName());
                }
                setupSpinner();
            }

            @Override
            public void onError(String error) {
                // Fallback hardcoded list
                String[] fallbackIngredients = {
                        "Chicken", "Beef", "Pork", "Fish", "Rice", "Pasta",
                        "Tomato", "Onion", "Garlic", "Cheese", "Eggs", "Milk"
                };
                useFallbackOptions(fallbackIngredients);
            }
        });
    }

    private void loadDietaryOptions() {
        // Dietary options are static lists
        String[] dietaryOptions = {
                "Vegan", "Vegetarian", "Gluten-Free", "Dairy-Free", "Low-Carb", "Keto"
        };
        useFallbackOptions(dietaryOptions);
    }

    // Helper to populate list from array
    private void useFallbackOptions(String[] options) {
        filterOptions.clear();
        for (String option : options) {
            filterOptions.add(option);
        }
        setupSpinner();
    }

    private void setupSpinner() {
        if (getContext() == null) return;

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                filterOptions
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        if (!filterOptions.isEmpty()) {
            spinner.setSelection(0);
        }

        dataLoaded = true;
        btnApply.setEnabled(true);
    }

    private void applyFilter() {
        if (spinner.getSelectedItem() != null) {
            String selectedValue = spinner.getSelectedItem().toString();

            if (listener != null) {
                listener.onFilterSelected(filterType, selectedValue);
            }
            dismiss();
        } else {
            Toast.makeText(requireContext(), "Please select an option", Toast.LENGTH_SHORT).show();
        }
    }
}