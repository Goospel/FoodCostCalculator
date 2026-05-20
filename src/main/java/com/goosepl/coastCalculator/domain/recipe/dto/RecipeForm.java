package com.goosepl.coastCalculator.domain.recipe.dto;

import com.goosepl.coastCalculator.domain.recipe.Recipe;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class RecipeForm {

    @NotBlank(message = "레시피명을 입력해주세요")
    @Size(max = 200, message = "레시피명은 200자 이하여야 합니다")
    private String name;

    @Min(value = 1, message = "인분은 1 이상이어야 합니다")
    @Max(value = 1000, message = "인분은 1000 이하여야 합니다")
    private int servings = 1;

    private List<RecipeIngredientForm> ingredients = new ArrayList<>();

    public static RecipeForm emptyWithRows(int rows) {
        RecipeForm f = new RecipeForm();
        for (int i = 0; i < rows; i++) {
            f.ingredients.add(new RecipeIngredientForm());
        }
        return f;
    }

    public static RecipeForm fromRecipe(Recipe recipe, int minRows) {
        RecipeForm f = new RecipeForm();
        f.name = recipe.getName();
        f.servings = recipe.getServings();
        recipe.getIngredients().forEach(ri -> f.ingredients.add(RecipeIngredientForm.from(ri)));
        while (f.ingredients.size() < minRows) {
            f.ingredients.add(new RecipeIngredientForm());
        }
        return f;
    }
}
