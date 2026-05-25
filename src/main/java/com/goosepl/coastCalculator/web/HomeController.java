package com.goosepl.coastCalculator.web;

import com.goosepl.coastCalculator.domain.recipe.Recipe;
import com.goosepl.coastCalculator.domain.recipe.RecipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
public class HomeController {

    // T2-7: 한 페이지 12개 (grid 3컬럼 × 4행에 맞춤). 최대 50으로 클램프해 비정상 쿼리 차단.
    private static final int DEFAULT_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 50;

    private final RecipeService recipeService;

    @GetMapping("/")
    public String home(@RequestParam(value = "q", required = false) String keyword,
                       @RequestParam(value = "page", defaultValue = "0") int page,
                       @RequestParam(value = "size", defaultValue = "12") int size,
                       Model model) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size));
        Page<Recipe> recipesPage = recipeService.searchByName(keyword, pageable);

        model.addAttribute("recipesPage", recipesPage);
        model.addAttribute("q", keyword);
        return "home";
    }

    private static int clampSize(int size) {
        if (size <= 0) return DEFAULT_PAGE_SIZE;
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
