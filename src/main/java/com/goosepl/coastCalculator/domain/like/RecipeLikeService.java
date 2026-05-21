package com.goosepl.coastCalculator.domain.like;

import com.goosepl.coastCalculator.domain.recipe.Recipe;
import com.goosepl.coastCalculator.domain.recipe.RecipeRepository;
import com.goosepl.coastCalculator.domain.user.User;
import com.goosepl.coastCalculator.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RecipeLikeService {

    private final RecipeLikeRepository likeRepository;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;

    /**
     * 좋아요 toggle. 이미 눌렀으면 취소, 안 눌렀으면 추가.
     * 반환값은 toggle 후 상태(true = 좋아요 활성).
     */
    @Transactional
    public boolean toggle(Long recipeId, String username) {
        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new IllegalArgumentException("레시피를 찾을 수 없습니다: id=" + recipeId));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + username));

        return likeRepository.findByRecipeAndUser(recipe, user)
                .map(existing -> {
                    likeRepository.delete(existing);
                    return false;
                })
                .orElseGet(() -> {
                    try {
                        likeRepository.save(RecipeLike.builder().recipe(recipe).user(user).build());
                        return true;
                    } catch (DataIntegrityViolationException e) {
                        // unique 제약 위반(동시 클릭) — 이미 좋아요 상태로 간주
                        log.debug("[Like] race condition handled for recipeId={}, user={}", recipeId, username);
                        return true;
                    }
                });
    }

    @Transactional(readOnly = true)
    public long count(Long recipeId) {
        return likeRepository.countByRecipeId(recipeId);
    }

    @Transactional(readOnly = true)
    public boolean isLikedBy(Long recipeId, String username) {
        if (username == null) {
            return false;
        }
        return likeRepository.existsByRecipeIdAndUserUsername(recipeId, username);
    }
}
