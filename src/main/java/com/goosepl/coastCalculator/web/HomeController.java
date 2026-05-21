package com.goosepl.coastCalculator.web;

import com.goosepl.coastCalculator.domain.recipe.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private static final int DEFAULT_LIMIT = 20;

    private final RecipeService recipeService;

    @GetMapping("/")
    public String home(@RequestParam(value = "q", required = false) String keyword,
                       Model model) {
        model.addAttribute("recipes", recipeService.searchByName(keyword, DEFAULT_LIMIT));
        model.addAttribute("q", keyword);
        return "home";
    }
}
