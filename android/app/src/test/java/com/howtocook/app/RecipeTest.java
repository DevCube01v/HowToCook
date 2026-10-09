package com.howtocook.app;
import org.junit.Test;
import static org.junit.Assert.*;
public class RecipeTest {
    private final Recipe recipe = new Recipe("dishes/vegetable_dish/a.md", "番茄炒蛋", "素菜", "★", "100", "content/a.html", "番茄炒蛋\n鸡蛋 盐\n焯水 10ml");
    @Test public void searchesIngredientsAndSteps() { assertTrue(recipe.matches("鸡蛋 焯水", "全部分类")); }
    @Test public void allTermsMustMatch() { assertFalse(recipe.matches("鸡蛋 牛肉", "全部分类")); }
    @Test public void categoryAndQueryIntersect() { assertTrue(recipe.matches("盐", "素菜")); assertFalse(recipe.matches("盐", "荤菜")); }
    @Test public void whitespaceAndFullWidthAreNormalized() { assertTrue(recipe.matches("  １０ＭＬ  \n 鸡蛋 ", "全部分类")); }
    @Test public void emptyQueryIncludesRecipe() { assertTrue(recipe.matches("", "全部分类")); }
}
