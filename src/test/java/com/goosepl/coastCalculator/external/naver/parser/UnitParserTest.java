package com.goosepl.coastCalculator.external.naver.parser;

import com.goosepl.coastCalculator.domain.ingredient.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UnitParserTest {

    @Test
    @DisplayName("kg 단위는 g로 변환 (×1000)")
    void parseKilogram() {
        var result = UnitParser.parse("CJ 백설 다목적 밀가루 1kg");
        assertThat(result).isPresent();
        assertThat(result.get().amount()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(result.get().unit()).isEqualTo(Unit.G);
    }

    @Test
    @DisplayName("대용량 kg 단위 처리")
    void parseLargeKilogram() {
        var result = UnitParser.parse("오뚜기 다목적 밀가루 20kg");
        assertThat(result).isPresent();
        assertThat(result.get().amount()).isEqualByComparingTo(new BigDecimal("20000"));
        assertThat(result.get().unit()).isEqualTo(Unit.G);
    }

    @Test
    @DisplayName("g 단위는 그대로 유지")
    void parseGram() {
        var result = UnitParser.parse("대상 청정원 소금 500g");
        assertThat(result).isPresent();
        assertThat(result.get().amount()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(result.get().unit()).isEqualTo(Unit.G);
    }

    @Test
    @DisplayName("L 단위는 ml로 변환 (×1000), 소수점 허용")
    void parseLiter() {
        var result = UnitParser.parse("샘표 진간장 1.5L");
        assertThat(result).isPresent();
        assertThat(result.get().amount()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(result.get().unit()).isEqualTo(Unit.ML);
    }

    @Test
    @DisplayName("ml 단위는 그대로 유지")
    void parseMilliliter() {
        var result = UnitParser.parse("우유 200ml");
        assertThat(result).isPresent();
        assertThat(result.get().amount()).isEqualByComparingTo(new BigDecimal("200"));
        assertThat(result.get().unit()).isEqualTo(Unit.ML);
    }

    @Test
    @DisplayName("쉼표 소수 표기 처리 (1,5kg)")
    void parseCommaDecimal() {
        var result = UnitParser.parse("쌀 1,5kg");
        assertThat(result).isPresent();
        assertThat(result.get().amount()).isEqualByComparingTo(new BigDecimal("1500"));
        assertThat(result.get().unit()).isEqualTo(Unit.G);
    }

    @Test
    @DisplayName("여러 매칭 시 첫 번째만 사용 (1kg×3봉 → 1kg)")
    void parseFirstMatchOnly() {
        var result = UnitParser.parse("라면 한박스 1kg×3봉");
        assertThat(result).isPresent();
        assertThat(result.get().amount()).isEqualByComparingTo(new BigDecimal("1000"));
        assertThat(result.get().unit()).isEqualTo(Unit.G);
    }

    @Test
    @DisplayName("대소문자 혼합 (KG, ML 등)")
    void parseCaseInsensitive() {
        assertThat(UnitParser.parse("쌀 5KG")).isPresent();
        assertThat(UnitParser.parse("우유 500ML")).isPresent();
    }

    @ParameterizedTest(name = "단위 없는 문자열 \"{0}\" → empty")
    @ValueSource(strings = {
            "다목적 밀가루",
            "오뚜기 즉석밥",
            ""
    })
    @DisplayName("단위가 없으면 empty 반환")
    void parseFailureNoUnit(String title) {
        assertThat(UnitParser.parse(title)).isEmpty();
    }

    @Test
    @DisplayName("null 입력은 empty 반환")
    void parseNull() {
        assertThat(UnitParser.parse(null)).isEmpty();
    }

    @Test
    @DisplayName("0이나 음수는 empty 반환")
    void parseZeroAmount() {
        assertThat(UnitParser.parse("이상한 제품 0kg")).isEmpty();
    }
}
