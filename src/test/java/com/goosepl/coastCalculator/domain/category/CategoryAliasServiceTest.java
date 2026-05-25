package com.goosepl.coastCalculator.domain.category;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * T3-18.2: CategoryAliasService 단위 테스트.
 *
 * - resolve: canonical 우선, alias 풀이, 없으면 그대로
 * - add: 5가지 검증 (blank / 자기자신 / canonical 중복 / alias 중복 / canonical 미존재)
 * - delete: 미존재 ID 거부
 */
@ExtendWith(MockitoExtension.class)
class CategoryAliasServiceTest {

    @Mock
    private CategoryAliasRepository aliasRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @InjectMocks
    private CategoryAliasService aliasService;

    @Nested
    @DisplayName("resolve — alias 풀이")
    class Resolve {

        @Test
        @DisplayName("input이 categories.name에 있으면 input 그대로 (canonical 우선)")
        void canonicalReturnsAsIs() {
            given(categoryRepository.existsByName("밀가루")).willReturn(true);

            String result = aliasService.resolve("밀가루");

            assertThat(result).isEqualTo("밀가루");
            verify(aliasRepository, never()).findByAlias(any());
        }

        @Test
        @DisplayName("input이 alias면 canonical 이름 반환")
        void aliasResolvesToCanonical() {
            Category canonical = Category.builder().name("밀가루").build();
            CategoryAlias alias = CategoryAlias.builder().alias("박력분").canonical(canonical).build();
            given(categoryRepository.existsByName("박력분")).willReturn(false);
            given(aliasRepository.findByAlias("박력분")).willReturn(Optional.of(alias));

            String result = aliasService.resolve("박력분");

            assertThat(result).isEqualTo("밀가루");
        }

        @Test
        @DisplayName("input이 canonical도 alias도 아니면 input 그대로 (사용자 자유 입력 보존)")
        void unknownReturnsAsIs() {
            given(categoryRepository.existsByName("미지의재료")).willReturn(false);
            given(aliasRepository.findByAlias("미지의재료")).willReturn(Optional.empty());

            String result = aliasService.resolve("미지의재료");

            assertThat(result).isEqualTo("미지의재료");
        }

        @Test
        @DisplayName("앞뒤 공백 trim 후 resolve")
        void trimsBeforeResolve() {
            given(categoryRepository.existsByName("밀가루")).willReturn(true);

            String result = aliasService.resolve("  밀가루  ");

            assertThat(result).isEqualTo("밀가루");
        }

        @Test
        @DisplayName("null input → null 반환 (정규화 없이 그대로)")
        void nullReturnsNull() {
            assertThat(aliasService.resolve(null)).isNull();
            verify(categoryRepository, never()).existsByName(any());
            verify(aliasRepository, never()).findByAlias(any());
        }

        @Test
        @DisplayName("blank input → blank 그대로 (categoriesRepository 호출 X)")
        void blankReturnsBlank() {
            assertThat(aliasService.resolve("   ")).isEmpty();
            verify(categoryRepository, never()).existsByName(any());
            verify(aliasRepository, never()).findByAlias(any());
        }
    }

    @Nested
    @DisplayName("add — alias 등록 검증")
    class Add {

        @Test
        @DisplayName("정상: canonical 존재 + alias 새 이름 → 저장")
        void addsValidAlias() {
            Category canonical = Category.builder().name("밀가루").build();
            given(categoryRepository.existsByName("박력분")).willReturn(false);
            given(aliasRepository.existsByAlias("박력분")).willReturn(false);
            given(categoryRepository.findByName("밀가루")).willReturn(Optional.of(canonical));
            given(aliasRepository.save(any(CategoryAlias.class))).willAnswer(inv -> inv.getArgument(0));

            CategoryAlias saved = aliasService.add("박력분", "밀가루");

            assertThat(saved.getAlias()).isEqualTo("박력분");
            assertThat(saved.getCanonical().getName()).isEqualTo("밀가루");
        }

        @Test
        @DisplayName("alias blank → IllegalArgumentException")
        void rejectsBlankAlias() {
            assertThatThrownBy(() -> aliasService.add("  ", "밀가루"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("alias 이름");
        }

        @Test
        @DisplayName("canonical blank → IllegalArgumentException")
        void rejectsBlankCanonical() {
            assertThatThrownBy(() -> aliasService.add("박력분", ""))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("canonical 카테고리");
        }

        @Test
        @DisplayName("alias == canonical → IllegalArgumentException")
        void rejectsSelfReference() {
            assertThatThrownBy(() -> aliasService.add("밀가루", "밀가루"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("같을 수 없");
        }

        @Test
        @DisplayName("alias가 이미 canonical 이름이면 거부")
        void rejectsAliasCollidingWithCanonical() {
            given(categoryRepository.existsByName("설탕")).willReturn(true);

            assertThatThrownBy(() -> aliasService.add("설탕", "밀가루"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("canonical 카테고리로 등록된");
        }

        @Test
        @DisplayName("alias 이미 등록 → 거부")
        void rejectsDuplicateAlias() {
            given(categoryRepository.existsByName("박력분")).willReturn(false);
            given(aliasRepository.existsByAlias("박력분")).willReturn(true);

            assertThatThrownBy(() -> aliasService.add("박력분", "밀가루"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("이미 등록된 alias");
        }

        @Test
        @DisplayName("canonical 카테고리 미존재 → 거부")
        void rejectsUnknownCanonical() {
            given(categoryRepository.existsByName("박력분")).willReturn(false);
            given(aliasRepository.existsByAlias("박력분")).willReturn(false);
            given(categoryRepository.findByName("밀가루")).willReturn(Optional.empty());

            assertThatThrownBy(() -> aliasService.add("박력분", "밀가루"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("canonical 카테고리를 찾을 수 없");
        }
    }

    @Test
    @DisplayName("delete: 미존재 ID → IllegalArgumentException")
    void deleteRejectsUnknownId() {
        given(aliasRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> aliasService.delete(999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alias를 찾을 수 없");
    }
}
