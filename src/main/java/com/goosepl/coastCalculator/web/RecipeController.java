package com.goosepl.coastCalculator.web;

import com.goosepl.coastCalculator.domain.recipe.Recipe;
import com.goosepl.coastCalculator.domain.recipe.RecipeService;
import com.goosepl.coastCalculator.domain.recipe.cost.PricingPolicy;
import com.goosepl.coastCalculator.domain.recipe.cost.RecipeCostCalculator;
import com.goosepl.coastCalculator.domain.recipe.cost.RecipeCostResult;
import com.goosepl.coastCalculator.domain.recipe.dto.RecipeForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;

@Controller
@RequestMapping("/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private static final int FORM_ROWS = 10;

    private final RecipeService recipeService;
    private final RecipeCostCalculator recipeCostCalculator;

    @GetMapping
    public String list(Principal principal, Model model) {
        model.addAttribute("recipes", recipeService.findMyRecipes(principal.getName()));
        return "recipes/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("recipeForm")) {
            model.addAttribute("recipeForm", RecipeForm.emptyWithRows(FORM_ROWS));
        }
        model.addAttribute("mode", "new");
        return "recipes/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute RecipeForm recipeForm,
                         BindingResult bindingResult,
                         Principal principal,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("mode", "new");
            return "recipes/form";
        }
        try {
            Long id = recipeService.create(recipeForm, principal.getName());
            return "redirect:/recipes/" + id;
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("mode", "new");
            return "recipes/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(value = "policy", required = false) PricingPolicy policy,
                         Model model) {
        Recipe recipe = recipeService.findForView(id);
        PricingPolicy effectivePolicy = (policy != null) ? policy : PricingPolicy.LOWEST;
        RecipeCostResult cost = recipeCostCalculator.calculate(recipe, effectivePolicy);
        model.addAttribute("recipe", recipe);
        model.addAttribute("cost", cost);
        model.addAttribute("policy", effectivePolicy);
        model.addAttribute("policies", PricingPolicy.values());
        return "recipes/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Principal principal, Model model) {
        Recipe recipe = recipeService.findMine(id, principal.getName());
        model.addAttribute("recipe", recipe);
        if (!model.containsAttribute("recipeForm")) {
            model.addAttribute("recipeForm", RecipeForm.fromRecipe(recipe, FORM_ROWS));
        }
        model.addAttribute("mode", "edit");
        return "recipes/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute RecipeForm recipeForm,
                         BindingResult bindingResult,
                         Principal principal,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("recipe", recipeService.findMine(id, principal.getName()));
            model.addAttribute("mode", "edit");
            return "recipes/form";
        }
        try {
            recipeService.update(id, recipeForm, principal.getName());
            return "redirect:/recipes/" + id;
        } catch (IllegalArgumentException e) {
            model.addAttribute("recipe", recipeService.findMine(id, principal.getName()));
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("mode", "edit");
            return "recipes/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal) {
        recipeService.delete(id, principal.getName());
        return "redirect:/recipes";
    }
}
