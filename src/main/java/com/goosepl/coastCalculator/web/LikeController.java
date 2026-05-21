package com.goosepl.coastCalculator.web;

import com.goosepl.coastCalculator.domain.like.RecipeLikeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class LikeController {

    private final RecipeLikeService recipeLikeService;

    /**
     * 좋아요 toggle. 인증 필요(SecurityConfig의 POST /recipes/** 룰에 의해).
     */
    @PostMapping("/recipes/{id}/like")
    public String toggleLike(@PathVariable Long id, Principal principal) {
        recipeLikeService.toggle(id, principal.getName());
        return "redirect:/recipes/" + id;
    }
}
