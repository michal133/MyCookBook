package com.example.cookbook.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.example.cookbook.R;
import com.example.cookbook.api.model.AreaResponse;
import com.example.cookbook.api.model.CategoryResponse;
import com.example.cookbook.api.model.IngredientResponse;
import com.example.cookbook.model.RecipeFilter;
import com.example.cookbook.util.FirebaseManager;

import java.util.ArrayList;
import java.util.List;

public class RecipeFilterDialog extends DialogFragment {

    private static final String TAG = "RecipeFilterDialog";
    private FirebaseManager firebaseManager;
    private OnFilterAppliedListener listener;

    // UI Components
    private RadioGroup radioGroupFilterType, radioGroupDietary;
    private LinearLayout layoutCategory, layoutArea, layoutIngredient, layoutDietary;
    private Spinner spinnerCategory, spinnerArea, spinnerIngredient;
    private Button btnApply, btnCancel;

    // Data Lists
    private List<CategoryResponse.Category> categories = new ArrayList<>();
    private List<AreaResponse.Area> areas = new ArrayList<>();
    private List<IngredientResponse.Ingredient> ingredients = new ArrayList<>();

    // Loading Flags
    private boolean categoriesLoaded = false;
    private boolean areasLoaded = false;
    private boolean ingredientsLoaded = false;

    public interface OnFilterAppliedListener {
        void onFilterApplied(RecipeFilter filter);
    }

    // Factory method to create a new instance with arguments
    public static RecipeFilterDialog newInstance(
            ArrayList<CategoryResponse.Category> categories,
            ArrayList<AreaResponse.Area> areas,
            ArrayList<IngredientResponse.Ingredient> ingredients) {
        RecipeFilterDialog dialog = new RecipeFilterDialog();
        Bundle args = new Bundle();
        args.putSerializable("categories", categories);
        args.putSerializable("areas", areas);
        args.putSerializable("ingredients", ingredients);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // Ensure the parent implements the listener interface
        if (getParentFragment() instanceof OnFilterAppliedListener) {
            listener = (OnFilterAppliedListener) getParentFragment();
        } else if (context instanceof OnFilterAppliedListener) {
            listener = (OnFilterAppliedListener) context;
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        firebaseManager = FirebaseManager.getInstance();

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(R.layout.dialog_recipe_filter, null);

        initViews(view);
        loadArguments();
        setupListeners();

        // Check if data is already loaded or fetch it
        if (categoriesLoaded && areasLoaded && ingredientsLoaded) {
            setupCategorySpinner();
            setupAreaSpinner();
            setupIngredientSpinner();
            checkCanEnableApplyButton();
        } else {
            loadFilterOptions();
        }

        builder.setView(view);
        return builder.create();
    }

    private void initViews(View view) {
        radioGroupFilterType = view.findViewById(R.id.radioGroupFilterType);
        radioGroupDietary = view.findViewById(R.id.radioGroupDietary);

        layoutCategory = view.findViewById(R.id.layoutCategory);
        layoutArea = view.findViewById(R.id.layoutArea);
        layoutIngredient = view.findViewById(R.id.layoutIngredient);
        layoutDietary = view.findViewById(R.id.layoutDietary);

        spinnerCategory = view.findViewById(R.id.spinnerCategory);
        spinnerArea = view.findViewById(R.id.spinnerArea);
        spinnerIngredient = view.findViewById(R.id.spinnerIngredient);

        btnApply = view.findViewById(R.id.btnApply);
        btnCancel = view.findViewById(R.id.btnCancel);
    }

    private void loadArguments() {
        Bundle args = getArguments();
        if (args != null) {
            ArrayList<CategoryResponse.Category> argCategories = (ArrayList<CategoryResponse.Category>) args.getSerializable("categories");
            ArrayList<AreaResponse.Area> argAreas = (ArrayList<AreaResponse.Area>) args.getSerializable("areas");
            ArrayList<IngredientResponse.Ingredient> argIngredients = (ArrayList<IngredientResponse.Ingredient>) args.getSerializable("ingredients");

            if (argCategories != null && !argCategories.isEmpty()) {
                categories = argCategories;
                categoriesLoaded = true;
            }
            if (argAreas != null && !argAreas.isEmpty()) {
                areas = argAreas;
                areasLoaded = true;
            }
            if (argIngredients != null && !argIngredients.isEmpty()) {
                ingredients = argIngredients;
                ingredientsLoaded = true;
            }
        }
    }

    private void setupListeners() {
        // Toggle visibility based on selected filter type
        radioGroupFilterType.setOnCheckedChangeListener((group, checkedId) -> {
            layoutCategory.setVisibility(View.GONE);
            layoutArea.setVisibility(View.GONE);
            layoutIngredient.setVisibility(View.GONE);
            layoutDietary.setVisibility(View.GONE);

            if (checkedId == R.id.radioCategory) {
                layoutCategory.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.radioArea) {
                layoutArea.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.radioIngredient) {
                layoutIngredient.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.radioDietary) {
                layoutDietary.setVisibility(View.VISIBLE);
            }
            checkCanEnableApplyButton();
        });

        radioGroupDietary.setOnCheckedChangeListener((group, checkedId) -> checkCanEnableApplyButton());

        btnApply.setOnClickListener(v -> applyFilter());
        btnCancel.setOnClickListener(v -> dismiss());

        // Initially disable Apply button until valid selection
        btnApply.setEnabled(false);
    }

    private void loadFilterOptions() {
        // Set a timeout to use default data if API is slow
        new Handler().postDelayed(() -> {
            if (!categoriesLoaded) {
                categories = getDefaultCategories();
                categoriesLoaded = true;
                setupCategorySpinner();
            }
            if (!areasLoaded) {
                areas = getDefaultAreas();
                areasLoaded = true;
                setupAreaSpinner();
            }
            if (!ingredientsLoaded) {
                ingredients = getDefaultIngredients();
                ingredientsLoaded = true;
                setupIngredientSpinner();
            }
            checkCanEnableApplyButton();
        }, 5000);

        // Fetch Categories
        firebaseManager.getCategories(new FirebaseManager.OnCategoriesLoadedListener() {
            @Override
            public void onCategoriesLoaded(List<CategoryResponse.Category> data) {
                categories = data;
                categoriesLoaded = true;
                setupCategorySpinner();
                checkCanEnableApplyButton();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to load categories: " + error);
            }
        });

        // Fetch Areas
        firebaseManager.getAreas(new FirebaseManager.OnAreasLoadedListener() {
            @Override
            public void onAreasLoaded(List<AreaResponse.Area> data) {
                areas = data;
                areasLoaded = true;
                setupAreaSpinner();
                checkCanEnableApplyButton();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to load areas: " + error);
            }
        });

        // Fetch Ingredients
        firebaseManager.getIngredients(new FirebaseManager.OnIngredientsLoadedListener() {
            @Override
            public void onIngredientsLoaded(List<IngredientResponse.Ingredient> data) {
                ingredients = data;
                ingredientsLoaded = true;
                setupIngredientSpinner();
                checkCanEnableApplyButton();
            }

            @Override
            public void onError(String error) {
                Log.e(TAG, "Failed to load ingredients: " + error);
            }
        });
    }

    private void checkCanEnableApplyButton() {
        if (!categoriesLoaded || !areasLoaded || !ingredientsLoaded) {
            btnApply.setEnabled(false);
            return;
        }

        int checkedId = radioGroupFilterType.getCheckedRadioButtonId();
        boolean enable = false;

        if (checkedId == R.id.radioCategory) {
            enable = spinnerCategory.getCount() > 0 && spinnerCategory.getSelectedItem() != null;
        } else if (checkedId == R.id.radioArea) {
            enable = spinnerArea.getCount() > 0 && spinnerArea.getSelectedItem() != null;
        } else if (checkedId == R.id.radioIngredient) {
            enable = spinnerIngredient.getCount() > 0 && spinnerIngredient.getSelectedItem() != null;
        } else if (checkedId == R.id.radioDietary) {
            enable = radioGroupDietary.getCheckedRadioButtonId() != -1;
        }

        btnApply.setEnabled(enable);
    }

    private void setupCategorySpinner() {
        List<String> names = new ArrayList<>();
        for (CategoryResponse.Category item : categories) {
            names.add(item.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerCategory.setAdapter(adapter);
    }

    private void setupAreaSpinner() {
        List<String> names = new ArrayList<>();
        for (AreaResponse.Area item : areas) {
            names.add(item.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerArea.setAdapter(adapter);
    }

    private void setupIngredientSpinner() {
        List<String> names = new ArrayList<>();
        for (IngredientResponse.Ingredient item : ingredients) {
            names.add(item.getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerIngredient.setAdapter(adapter);
    }

    private void applyFilter() {
        RecipeFilter filter = null;
        int checkedId = radioGroupFilterType.getCheckedRadioButtonId();

        if (checkedId == R.id.radioCategory) {
            if (spinnerCategory.getSelectedItem() != null) {
                filter = RecipeFilter.byCategory(spinnerCategory.getSelectedItem().toString());
            }
        } else if (checkedId == R.id.radioArea) {
            if (spinnerArea.getSelectedItem() != null) {
                filter = RecipeFilter.byArea(spinnerArea.getSelectedItem().toString());
            }
        } else if (checkedId == R.id.radioIngredient) {
            if (spinnerIngredient.getSelectedItem() != null) {
                filter = RecipeFilter.byIngredient(spinnerIngredient.getSelectedItem().toString());
            }
        } else if (checkedId == R.id.radioDietary) {
            int dietaryId = radioGroupDietary.getCheckedRadioButtonId();
            if (dietaryId == R.id.radioVegan) {
                filter = RecipeFilter.veganOnly();
            } else if (dietaryId == R.id.radioVegetarian) {
                filter = RecipeFilter.vegetarianOnly();
            } else if (dietaryId == R.id.radioGlutenFree) {
                filter = RecipeFilter.glutenFreeOnly();
            }
        }

        if (filter != null && listener != null) {
            listener.onFilterApplied(filter);
            dismiss();
        } else {
            Toast.makeText(requireContext(), "Please select a filter", Toast.LENGTH_SHORT).show();
        }
    }

    // --- Fallback Data Methods (used if API fails) ---

    private List<CategoryResponse.Category> getDefaultCategories() {
        List<CategoryResponse.Category> list = new ArrayList<>();
        String[] names = {"Beef", "Chicken", "Dessert", "Lamb", "Pasta", "Pork", "Seafood", "Vegan", "Vegetarian"};
        for (int i = 0; i < names.length; i++) list.add(createCategory(String.valueOf(i), names[i]));
        return list;
    }

    private List<AreaResponse.Area> getDefaultAreas() {
        List<AreaResponse.Area> list = new ArrayList<>();
        String[] names = {"American", "British", "Chinese", "French", "Indian", "Italian", "Japanese", "Mexican"};
        for (String name : names) list.add(createArea(name));
        return list;
    }

    private List<IngredientResponse.Ingredient> getDefaultIngredients() {
        List<IngredientResponse.Ingredient> list = new ArrayList<>();
        String[] names = {"Chicken", "Beef", "Rice", "Pasta", "Tomato", "Cheese", "Eggs"};
        for (int i = 0; i < names.length; i++) list.add(createIngredient(String.valueOf(i), names[i]));
        return list;
    }

    // Helper methods to create mock objects using reflection (since setters might be private)
    private CategoryResponse.Category createCategory(String id, String name) {
        try {
            CategoryResponse.Category obj = new CategoryResponse.Category();
            java.lang.reflect.Field nameField = CategoryResponse.Category.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(obj, name);
            return obj;
        } catch (Exception e) { return null; }
    }

    private AreaResponse.Area createArea(String name) {
        try {
            AreaResponse.Area obj = new AreaResponse.Area();
            java.lang.reflect.Field nameField = AreaResponse.Area.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(obj, name);
            return obj;
        } catch (Exception e) { return null; }
    }

    private IngredientResponse.Ingredient createIngredient(String id, String name) {
        try {
            IngredientResponse.Ingredient obj = new IngredientResponse.Ingredient();
            java.lang.reflect.Field nameField = IngredientResponse.Ingredient.class.getDeclaredField("name");
            nameField.setAccessible(true);
            nameField.set(obj, name);
            return obj;
        } catch (Exception e) { return null; }
    }
}