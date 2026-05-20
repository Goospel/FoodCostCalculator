package com.goosepl.coastCalculator.web;

import com.goosepl.coastCalculator.domain.ingredient.Ingredient;
import com.goosepl.coastCalculator.domain.ingredient.IngredientService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/ingredients")
@RequiredArgsConstructor
public class IngredientController {

    private final IngredientService ingredientService;

    @GetMapping
    public String list(@RequestParam(value = "category", required = false) String category,
                       Model model) {
        List<Ingredient> ingredients = (category == null || category.isBlank())
                ? ingredientService.findAllVisible()
                : ingredientService.viewByCategory(category);

        model.addAttribute("ingredients", ingredients);
        model.addAttribute("category", category);
        return "ingredients/list";
    }
}
