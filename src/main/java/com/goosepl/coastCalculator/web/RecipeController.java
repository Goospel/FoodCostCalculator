package com.goosepl.coastCalculator.web;

import com.goosepl.coastCalculator.domain.comment.CommentService;
import com.goosepl.coastCalculator.domain.ingredient.Ingredient;
import com.goosepl.coastCalculator.domain.ingredient.IngredientService;
import com.goosepl.coastCalculator.domain.like.RecipeLikeService;
import com.goosepl.coastCalculator.domain.recipe.Recipe;
import com.goosepl.coastCalculator.domain.recipe.RecipeService;
import com.goosepl.coastCalculator.domain.recipe.cost.PricingPolicy;
import com.goosepl.coastCalculator.domain.recipe.cost.RecipeCostCalculator;
import com.goosepl.coastCalculator.domain.recipe.cost.RecipeCostResult;
import com.goosepl.coastCalculator.domain.recipe.dto.RecipeForm;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/recipes")
@RequiredArgsConstructor
public class RecipeController {

    private static final int FORM_ROWS = 10;
    // T2-7: 내 레시피 목록도 페이징
    private static final int LIST_PAGE_SIZE = 12;
    private static final int MAX_PAGE_SIZE = 50;

    private final RecipeService recipeService;
    private final RecipeCostCalculator recipeCostCalculator;
    private final RecipeLikeService recipeLikeService;
    private final CommentService commentService;
    private final IngredientService ingredientService;

    @GetMapping
    public String list(@RequestParam(value = "page", defaultValue = "0") int page,
                       @RequestParam(value = "size", defaultValue = "12") int size,
                       Principal principal, Model model) {
        int safeSize = (size <= 0) ? LIST_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(Math.max(page, 0), safeSize);
        model.addAttribute("recipesPage", recipeService.findMyRecipes(principal.getName(), pageable));
        return "recipes/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("recipeForm")) {
            model.addAttribute("recipeForm", RecipeForm.emptyWithRows(FORM_ROWS));
        }
        model.addAttribute("ingredientGroups", loadIngredientGroups());
        model.addAttribute("mode", "new");
        return "recipes/form";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute RecipeForm recipeForm,
                         BindingResult bindingResult,
                         @RequestParam(value = "image", required = false) MultipartFile image,
                         Principal principal,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("ingredientGroups", loadIngredientGroups());
            model.addAttribute("mode", "new");
            return "recipes/form";
        }
        try {
            Long id = recipeService.create(recipeForm, principal.getName(), image);
            return "redirect:/recipes/" + id;
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("ingredientGroups", loadIngredientGroups());
            model.addAttribute("mode", "new");
            return "recipes/form";
        }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id,
                         @RequestParam(value = "policy", required = false) PricingPolicy policy,
                         Principal principal,
                         Model model) {
        Recipe recipe = recipeService.findForView(id);
        PricingPolicy effectivePolicy = (policy != null) ? policy : PricingPolicy.LOWEST;
        RecipeCostResult cost = recipeCostCalculator.calculate(recipe, effectivePolicy);

        String username = (principal != null) ? principal.getName() : null;
        long likeCount = recipeLikeService.count(id);
        boolean userLiked = recipeLikeService.isLikedBy(id, username);

        model.addAttribute("recipe", recipe);
        model.addAttribute("cost", cost);
        model.addAttribute("policy", effectivePolicy);
        model.addAttribute("policies", PricingPolicy.values());
        model.addAttribute("likeCount", likeCount);
        model.addAttribute("userLiked", userLiked);
        model.addAttribute("rootComments", commentService.listForRecipe(id));
        model.addAttribute("commentCount", commentService.countForRecipe(id));
        return "recipes/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Principal principal, Model model) {
        Recipe recipe = recipeService.findMine(id, principal.getName());
        model.addAttribute("recipe", recipe);
        if (!model.containsAttribute("recipeForm")) {
            model.addAttribute("recipeForm", RecipeForm.fromRecipe(recipe, FORM_ROWS));
        }
        model.addAttribute("ingredientGroups", loadIngredientGroups());
        model.addAttribute("mode", "edit");
        return "recipes/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute RecipeForm recipeForm,
                         BindingResult bindingResult,
                         @RequestParam(value = "image", required = false) MultipartFile image,
                         @RequestParam(value = "removeImage", defaultValue = "false") boolean removeImage,
                         Principal principal,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("recipe", recipeService.findMine(id, principal.getName()));
            model.addAttribute("ingredientGroups", loadIngredientGroups());
            model.addAttribute("mode", "edit");
            return "recipes/form";
        }
        try {
            recipeService.update(id, recipeForm, principal.getName(), image, removeImage);
            return "redirect:/recipes/" + id;
        } catch (IllegalArgumentException e) {
            model.addAttribute("recipe", recipeService.findMine(id, principal.getName()));
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("ingredientGroups", loadIngredientGroups());
            model.addAttribute("mode", "edit");
            return "recipes/form";
        }
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, Principal principal) {
        recipeService.delete(id, principal.getName());
        return "redirect:/recipes";
    }

    /**
     * T3-17: form.html의 selectedIngredientId 드롭다운 데이터.
     * 카테고리 부여된 ingredient만(findAllVisible), 카테고리 → 목록으로 그루핑.
     * findAllVisible이 category ASC + pricePerGram ASC로 정렬되어 옴 → LinkedHashMap으로 순서 보존.
     */
    private Map<String, List<Ingredient>> loadIngredientGroups() {
        return ingredientService.findAllVisible().stream()
                .collect(Collectors.groupingBy(
                        Ingredient::getCategory,
                        LinkedHashMap::new,
                        Collectors.toList()));
    }
}
