package com.goosepl.coastCalculator.domain.comment;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    /**
     * 레시피의 댓글 전체를 한 번에 fetch.
     * user(LAZY) join으로 N+1 회피. 정렬은 service에서 그루핑 후 처리.
     */
    @EntityGraph(attributePaths = {"user", "parent"})
    List<Comment> findByRecipeIdOrderByCreatedAtAsc(Long recipeId);

    long countByRecipeId(Long recipeId);
}
