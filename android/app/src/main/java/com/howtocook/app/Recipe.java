package com.howtocook.app;

import java.text.Normalizer;
import java.util.Locale;

final class Recipe {
    final String id, title, category, difficulty, calories, html, searchable, thumbnail;
    Recipe(String id, String title, String category, String difficulty, String calories, String html, String search) {
        this(id, title, category, difficulty, calories, html, search, "");
    }
    Recipe(String id, String title, String category, String difficulty, String calories, String html, String search, String thumbnail) {
        this.thumbnail = thumbnail;
        this.id = id; this.title = title; this.category = category;
        this.difficulty = difficulty; this.calories = calories; this.html = html;
        this.searchable = normalize(search);
    }
    static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }
    boolean matches(String query, String selectedCategory) {
        if (!selectedCategory.equals("全部分类") && !category.equals(selectedCategory)) return false;
        for (String term : normalize(query).trim().split("\\s+")) {
            if (!searchable.contains(term)) return false;
        }
        return true;
    }
}
