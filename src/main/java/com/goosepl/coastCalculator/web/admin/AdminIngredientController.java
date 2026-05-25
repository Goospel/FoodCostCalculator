package com.goosepl.coastCalculator.web.admin;

import com.goosepl.coastCalculator.domain.category.CategoryService;
import com.goosepl.coastCalculator.domain.ingredient.Ingredient;
import com.goosepl.coastCalculator.domain.ingredient.IngredientService;
import com.goosepl.coastCalculator.domain.ingredient.dto.CategoryUpdateForm;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/ingredients")
@RequiredArgsConstructor
public class AdminIngredientController {

    private final IngredientService ingredientService;
    private final CategoryService categoryService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("ingredients", ingredientService.findAllForAdmin());
        return "admin/ingredients/list";
    }

    @GetMapping("/fetch")
    public String fetchForm() {
        return "admin/ingredients/fetch";
    }

    @PostMapping("/fetch")
    public String fetch(@RequestParam("keyword") String keyword,
                        RedirectAttributes redirectAttributes) {
        int upserted = ingredientService.fetchAndUpsert(keyword);
        redirectAttributes.addFlashAttribute("flashMessage",
                "Naver 검색 '" + keyword + "' 결과 " + upserted + "건 반영 완료");
        return "redirect:/admin/ingredients";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        Ingredient ingredient = ingredientService.findById(id);
        model.addAttribute("ingredient", ingredient);
        if (!model.containsAttribute("categoryUpdateForm")) {
            model.addAttribute("categoryUpdateForm", new CategoryUpdateForm(ingredient.getCategory()));
        }
        // T3-18: 카테고리 마스터를 datalist 자동완성으로 노출
        model.addAttribute("categoryNames", categoryService.findAllNames());
        return "admin/ingredients/edit";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @Valid @ModelAttribute CategoryUpdateForm categoryUpdateForm,
                         BindingResult bindingResult,
                         Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("ingredient", ingredientService.findById(id));
            model.addAttribute("categoryNames", categoryService.findAllNames());
            return "admin/ingredients/edit";
        }
        ingredientService.updateCategory(id, categoryUpdateForm.getCategory());
        return "redirect:/admin/ingredients";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        ingredientService.delete(id);
        return "redirect:/admin/ingredients";
    }
}
