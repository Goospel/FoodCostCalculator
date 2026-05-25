package com.goosepl.coastCalculator.domain.category;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * T3-18: CategoryService 단위 테스트.
 *
 * 핵심:
 *  - ensureExists는 멱등 (이미 있으면 save X)
 *  - null/blank는 no-op
 *  - 마스터 자동완성용 이름 목록 반환
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryService categoryService;

    @Nested
    @DisplayName("ensureExists — 멱등 등록")
    class EnsureExists {

        @Test
        @DisplayName("새 카테고리 → save 호출")
        void insertsNewCategory() {
            given(categoryRepository.findByName("밀가루")).willReturn(Optional.empty());

            categoryService.ensureExists("밀가루");

            verify(categoryRepository, times(1)).save(any(Category.class));
        }

        @Test
        @DisplayName("이미 존재하는 카테고리 → save 호출 X")
        void skipsExistingCategory() {
            Category existing = Category.builder().name("밀가루").build();
            given(categoryRepository.findByName("밀가루")).willReturn(Optional.of(existing));

            categoryService.ensureExists("밀가루");

            verify(categoryRepository, never()).save(any(Category.class));
        }

        @Test
        @DisplayName("앞뒤 공백 trim 후 등록")
        void trimsWhitespace() {
            given(categoryRepository.findByName("설탕")).willReturn(Optional.empty());

            categoryService.ensureExists("  설탕  ");

            verify(categoryRepository).findByName("설탕");
            verify(categoryRepository).save(any(Category.class));
        }

        @Test
        @DisplayName("null → no-op")
        void noOpForNull() {
            categoryService.ensureExists(null);

            verify(categoryRepository, never()).findByName(any());
            verify(categoryRepository, never()).save(any());
        }

        @Test
        @DisplayName("blank → no-op")
        void noOpForBlank() {
            categoryService.ensureExists("   ");

            verify(categoryRepository, never()).findByName(any());
            verify(categoryRepository, never()).save(any());
        }
    }

    @Test
    @DisplayName("findAllNames: Repository 결과를 name 리스트로 매핑")
    void findAllNamesMapsRepositoryResults() {
        Category a = Category.builder().name("밀가루").build();
        Category b = Category.builder().name("설탕").build();
        given(categoryRepository.findAllByOrderByNameAsc()).willReturn(List.of(a, b));

        List<String> names = categoryService.findAllNames();

        assertThat(names).containsExactly("밀가루", "설탕");
    }
}
