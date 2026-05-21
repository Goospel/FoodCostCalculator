package com.goosepl.coastCalculator.domain.comment;

import com.goosepl.coastCalculator.domain.comment.dto.ReplyView;
import com.goosepl.coastCalculator.domain.comment.dto.RootCommentView;
import com.goosepl.coastCalculator.domain.recipe.Recipe;
import com.goosepl.coastCalculator.domain.recipe.RecipeRepository;
import com.goosepl.coastCalculator.domain.user.Role;
import com.goosepl.coastCalculator.domain.user.User;
import com.goosepl.coastCalculator.domain.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CommentService {

    private static final int MAX_CONTENT_LENGTH = 1000;

    private final CommentRepository commentRepository;
    private final RecipeRepository recipeRepository;
    private final UserRepository userRepository;

    @Transactional
    public Long create(Long recipeId, String rawContent, Long parentId, String username) {
        String content = (rawContent == null) ? "" : rawContent.trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("댓글 내용을 입력해주세요");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new IllegalArgumentException("댓글은 " + MAX_CONTENT_LENGTH + "자 이하로 작성해주세요");
        }

        Recipe recipe = recipeRepository.findById(recipeId)
                .orElseThrow(() -> new IllegalArgumentException("레시피를 찾을 수 없습니다: id=" + recipeId));
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다: " + username));

        Comment parent = null;
        if (parentId != null) {
            parent = commentRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("부모 댓글을 찾을 수 없습니다: id=" + parentId));
            if (!Objects.equals(parent.getRecipe().getId(), recipeId)) {
                throw new IllegalArgumentException("부모 댓글이 이 레시피의 것이 아닙니다");
            }
            if (parent.getParent() != null) {
                // 1단계 대댓글까지만 허용
                throw new IllegalArgumentException("대댓글에는 다시 답글을 달 수 없습니다");
            }
        }

        Comment comment = Comment.builder()
                .recipe(recipe)
                .user(user)
                .parent(parent)
                .content(content)
                .build();
        return commentRepository.save(comment).getId();
    }

    @Transactional
    public void delete(Long commentId, String username) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new IllegalArgumentException("댓글을 찾을 수 없습니다: id=" + commentId));
        if (!comment.isWrittenBy(username) && !isAdmin(username)) {
            throw new AccessDeniedException("이 댓글을 삭제할 권한이 없습니다");
        }
        commentRepository.delete(comment); // cascade로 대댓글도 함께 삭제됨
    }

    /**
     * 레시피의 모든 댓글을 1쿼리로 조회 후 루트/대댓글로 그루핑.
     * 정렬: 루트 createdAt ASC, 그 아래 대댓글 createdAt ASC.
     */
    @Transactional(readOnly = true)
    public List<RootCommentView> listForRecipe(Long recipeId) {
        List<Comment> all = commentRepository.findByRecipeIdOrderByCreatedAtAsc(recipeId);
        if (all.isEmpty()) {
            return List.of();
        }

        // LinkedHashMap으로 삽입 순서 보존(createdAt ASC가 이미 정렬됨)
        Map<Long, List<ReplyView>> repliesByParent = new LinkedHashMap<>();
        List<Comment> roots = new ArrayList<>();
        for (Comment c : all) {
            if (c.getParent() == null) {
                roots.add(c);
                repliesByParent.computeIfAbsent(c.getId(), k -> new ArrayList<>());
            } else {
                repliesByParent
                        .computeIfAbsent(c.getParent().getId(), k -> new ArrayList<>())
                        .add(new ReplyView(
                                c.getId(),
                                c.getUser().getUsername(),
                                c.getContent(),
                                c.getCreatedAt()
                        ));
            }
        }
        // 대댓글 정렬 (이미 ASC이지만 안전을 위해)
        repliesByParent.values().forEach(list -> list.sort(Comparator.comparing(ReplyView::createdAt)));

        List<RootCommentView> result = new ArrayList<>(roots.size());
        for (Comment root : roots) {
            result.add(new RootCommentView(
                    root.getId(),
                    root.getUser().getUsername(),
                    root.getContent(),
                    root.getCreatedAt(),
                    repliesByParent.getOrDefault(root.getId(), List.of())
            ));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public long countForRecipe(Long recipeId) {
        return commentRepository.countByRecipeId(recipeId);
    }

    private boolean isAdmin(String username) {
        return userRepository.findByUsername(username)
                .map(u -> u.getRole() == Role.ADMIN)
                .orElse(false);
    }
}
