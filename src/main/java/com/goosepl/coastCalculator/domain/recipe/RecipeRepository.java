package com.goosepl.coastCalculator.domain.recipe;

import com.goosepl.coastCalculator.domain.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * T2-11: ToMany 컬렉션(`ingredients`)을 페이징하면서 EntityGraph로 fetch하면
 * Hibernate가 `firstResult/maxResults specified with collection fetch; applying in memory`
 * 경고와 함께 전체 결과를 메모리에 올린 뒤 자르는 비효율이 발생한다.
 *
 * 해결: **two-step 쿼리 패턴**.
 *   1) ID-only Page 쿼리로 `Page<Long>`을 받음 (count + ID select, 정렬은 SQL에서)
 *   2) 그 ID 목록으로 EntityGraph + IN 절 + ORDER BY로 entity를 fetch
 *   3) `PageImpl<>(content, pageable, totalElements)` 로 재조합
 *
 * 단건 조회(`findById`, `findWithDetailsById`)는 페이징 무관이라 EntityGraph만으로 충분.
 */
public interface RecipeRepository extends JpaRepository<Recipe, Long> {

    // ---- T2-11: ID-only Page 쿼리 (collection fetch 미사용 → in-memory paging 회피) ----

    @Query("SELECT r.id FROM Recipe r ORDER BY r.createdAt DESC")
    Page<Long> findIdsAllByCreatedAtDesc(Pageable pageable);

    @Query("SELECT r.id FROM Recipe r " +
            "WHERE LOWER(r.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "ORDER BY r.createdAt DESC")
    Page<Long> findIdsByNameContainingIgnoreCaseOrderByCreatedAtDesc(
            @Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT r.id FROM Recipe r WHERE r.user = :user ORDER BY r.updatedAt DESC")
    Page<Long> findIdsByUserOrderByUpdatedAtDesc(@Param("user") User user, Pageable pageable);

    // ---- T2-11: 두 번째 단계 — IN 절 + EntityGraph + ORDER BY 보존 ----
    // ORDER BY는 IN의 결과 순서를 보장하지 않으므로 명시적으로 적어준다.

    @EntityGraph(attributePaths = {"user", "ingredients"})
    @Query("SELECT r FROM Recipe r WHERE r.id IN :ids ORDER BY r.createdAt DESC")
    List<Recipe> findAllWithDetailsByIdInOrderByCreatedAtDesc(@Param("ids") List<Long> ids);

    @EntityGraph(attributePaths = {"user", "ingredients"})
    @Query("SELECT r FROM Recipe r WHERE r.id IN :ids ORDER BY r.updatedAt DESC")
    List<Recipe> findAllWithDetailsByIdInOrderByUpdatedAtDesc(@Param("ids") List<Long> ids);

    // ---- 단건 조회 — 페이징 무관, EntityGraph만으로 충분 ----

    // T2-11: RecipeService.findMine에서 사용. ingredients 컬렉션을 EntityGraph로 한 번에 페치 →
    // `recipe.getIngredients().size()` 강제 초기화 패턴 제거.
    @EntityGraph(attributePaths = {"user", "ingredients"})
    Optional<Recipe> findWithUserAndIngredientsById(Long id);

    // T3-17: detail.html의 ri.selectedIngredient.title 접근까지 페치 필요
    @EntityGraph(attributePaths = {"user", "ingredients", "ingredients.selectedIngredient"})
    Optional<Recipe> findWithDetailsById(Long id);
}
