package com.goosepl.coastCalculator.web.admin;

import com.goosepl.coastCalculator.domain.category.CategoryAliasService;
import com.goosepl.coastCalculator.domain.category.CategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * T3-18.2: 카테고리 alias 관리 (admin 전용).
 *
 * GET  /admin/category-aliases              : 목록 + 추가 폼 (한 페이지)
 * POST /admin/category-aliases              : alias 추가
 * POST /admin/category-aliases/{id}/delete  : alias 삭제
 *
 * JS 없음 정책: 폼 POST + redirect. canonical 선택은 HTML5 datalist 자동완성.
 * 보안: SecurityConfig의 /admin/** ROLE_ADMIN 룰로 보호 (별도 설정 불필요).
 */
@Controller
@RequestMapping("/admin/category-aliases")
@RequiredArgsConstructor
public class AdminCategoryAliasController {

    private final CategoryAliasService categoryAliasService;
    private final CategoryService categoryService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("aliases", categoryAliasService.findAll());
        model.addAttribute("categoryNames", categoryService.findAllNames());
        return "admin/category-aliases/list";
    }

    @PostMapping
    public String add(@RequestParam("alias") String alias,
                      @RequestParam("canonical") String canonical,
                      RedirectAttributes redirectAttributes) {
        categoryAliasService.add(alias, canonical);
        redirectAttributes.addFlashAttribute("flashMessage",
                "alias 등록 완료: " + alias.trim() + " → " + canonical.trim());
        return "redirect:/admin/category-aliases";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        categoryAliasService.delete(id);
        redirectAttributes.addFlashAttribute("flashMessage", "alias 삭제 완료");
        return "redirect:/admin/category-aliases";
    }
}
