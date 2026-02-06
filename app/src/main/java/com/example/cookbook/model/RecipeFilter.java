package com.example.cookbook.model;

import java.util.List;
import java.util.ArrayList;

public class RecipeFilter {

    // Available filter types
    public enum FilterType {
        CATEGORY,
        AREA,
        INGREDIENT,
        SEARCH
    }

    private FilterType type;
    private String value;
    private List<String> values; // support for multiple selections

    // constructor for single value
    public RecipeFilter(FilterType type, String value) {
        this.type = type;
        this.value = value;
        this.values = new ArrayList<>();
        if (value != null) {
            this.values.add(value);
        }
    }

    // constructor for list of values
    public RecipeFilter(FilterType type, List<String> values) {
        this.type = type;
        this.values = values != null ? values : new ArrayList<>();
        // use first item as main value
        this.value = (values != null && !values.isEmpty()) ? values.get(0) : null;
    }

    public FilterType getType() { return type; }
    public String getValue() { return value; }
    public List<String> getValues() { return values; }


    public static RecipeFilter byCategory(String category) {
        return new RecipeFilter(FilterType.CATEGORY, category);
    }

    public static RecipeFilter byArea(String area) {
        return new RecipeFilter(FilterType.AREA, area);
    }

    public static RecipeFilter byIngredient(String ingredient) {
        return new RecipeFilter(FilterType.INGREDIENT, ingredient);
    }

    public static RecipeFilter bySearch(String query) {
        return new RecipeFilter(FilterType.SEARCH, query);
    }

    //special Dietary Filters

    public static RecipeFilter veganOnly() {
        // API doesn't have Vegan category, using Vegetarian as fallback
        return new RecipeFilter(FilterType.CATEGORY, "Vegetarian");
    }

    public static RecipeFilter vegetarianOnly() {
        return new RecipeFilter(FilterType.CATEGORY, "Vegetarian");
    }

    public static RecipeFilter glutenFreeOnly() {
        return new RecipeFilter(FilterType.CATEGORY, "Miscellaneous");
    }
}