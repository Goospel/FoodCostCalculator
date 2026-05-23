package com.goosepl.coastCalculator.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SecurityConfig의 권한 룰을 익명/USER/ADMIN 3가지 주체로 분기 검증.
 *
 * 검증 대상:
 *  - 공개 경로: /, /login, /signup, /uploads/**, GET /recipes/{id}
 *  - 인증 필요: /recipes 목록/생성, /recipes/{id}/edit, POST /recipes/**
 *  - 관리자 전용: /admin/**
 *
 * 정확한 상태 코드보다 "접근 가능 여부"가 핵심:
 *  - 익명이 보호된 경로 접근 → 302 redirect to /login
 *  - USER가 ADMIN 경로 접근 → 403
 *  - ADMIN이 ADMIN 경로 접근 → 200/302 (실패 X)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    // ─────── 공개 경로 ───────

    @Test
    @WithAnonymousUser
    @DisplayName("익명: GET / (홈) 접근 가능")
    void anonymousCanAccessHome() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("익명: GET /login 접근 가능")
    void anonymousCanAccessLogin() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("익명: GET /signup 접근 가능")
    void anonymousCanAccessSignup() throws Exception {
        mockMvc.perform(get("/signup"))
                .andExpect(status().isOk());
    }

    @Test
    @WithAnonymousUser
    @DisplayName("익명: GET /recipes/{id} 상세 페이지 접근 가능 (4xx OK, redirect 아님)")
    void anonymousCanAccessRecipeDetail() throws Exception {
        // 핵심: 302 redirect to /login이 안 떠야 한다. 존재하지 않는 ID는 4xx로 떨어짐.
        // (현재 GlobalExceptionHandler가 IllegalArgumentException을 400으로 매핑)
        mockMvc.perform(get("/recipes/99999"))
                .andExpect(status().is4xxClientError());
    }

    // ─────── 인증 필요 경로 — 익명 ───────

    @Test
    @WithAnonymousUser
    @DisplayName("익명: GET /recipes 목록 → 로그인 리다이렉트")
    void anonymousRecipesListRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/recipes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("익명: GET /recipes/new → 로그인 리다이렉트")
    void anonymousRecipesNewRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/recipes/new"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("익명: GET /recipes/{id}/edit → 로그인 리다이렉트")
    void anonymousRecipesEditRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/recipes/1/edit"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    @WithAnonymousUser
    @DisplayName("익명: POST /recipes → 로그인 리다이렉트")
    void anonymousPostRecipesRedirectsToLogin() throws Exception {
        mockMvc.perform(post("/recipes").with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ─────── 관리자 경로 — 익명 ───────

    @Test
    @WithAnonymousUser
    @DisplayName("익명: GET /admin/ingredients → 로그인 리다이렉트")
    void anonymousAdminRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin/ingredients"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    // ─────── 관리자 경로 — 일반 USER ───────

    @Test
    @WithMockUser(username = "alice", roles = {"USER"})
    @DisplayName("USER: GET /admin/ingredients → 403 Forbidden")
    void userCannotAccessAdmin() throws Exception {
        mockMvc.perform(get("/admin/ingredients"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", roles = {"USER"})
    @DisplayName("USER: GET /admin/ingredients/fetch → 403 Forbidden")
    void userCannotAccessAdminFetch() throws Exception {
        mockMvc.perform(get("/admin/ingredients/fetch"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "alice", roles = {"USER"})
    @DisplayName("USER: POST /admin/ingredients/fetch → 403 Forbidden")
    void userCannotPostAdminFetch() throws Exception {
        mockMvc.perform(post("/admin/ingredients/fetch").with(csrf()).param("keyword", "밀가루"))
                .andExpect(status().isForbidden());
    }

    // ─────── 관리자 경로 — ADMIN ───────

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("ADMIN: GET /admin/ingredients 접근 가능")
    void adminCanAccessAdmin() throws Exception {
        mockMvc.perform(get("/admin/ingredients"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "admin", roles = {"ADMIN"})
    @DisplayName("ADMIN: GET /admin/ingredients/fetch 접근 가능")
    void adminCanAccessAdminFetch() throws Exception {
        mockMvc.perform(get("/admin/ingredients/fetch"))
                .andExpect(status().isOk());
    }

    // ─────── 인증 필요 경로 — USER ───────

    @Test
    @WithMockUser(username = "alice", roles = {"USER"})
    @DisplayName("USER: GET /recipes/new 접근 가능")
    void userCanAccessRecipesNew() throws Exception {
        mockMvc.perform(get("/recipes/new"))
                .andExpect(status().isOk());
    }

    // ─────── 정적 리소스 ───────

    @Test
    @WithAnonymousUser
    @DisplayName("익명: GET /uploads/** 정적 리소스 접근 가능 (404 OK, redirect 아님)")
    void anonymousCanAccessUploads() throws Exception {
        // 존재하지 않는 파일 → 404. 핵심은 302 redirect to /login이 안 떠야 함
        mockMvc.perform(get("/uploads/recipes/nonexistent.jpg"))
                .andExpect(status().isNotFound());
    }
}
