package com.goosepl.coastCalculator.external.naver.parser;

import com.goosepl.coastCalculator.domain.ingredient.Unit;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UnitParser {

    private static final BigDecimal THOUSAND = BigDecimal.valueOf(1000);

    private static final Pattern UNIT_PATTERN = Pattern.compile(
            "(\\d+(?:[.,]\\d+)?)\\s*(kg|mg|ml|g|l)\\b",
            Pattern.CASE_INSENSITIVE);

    public record ParsedAmount(BigDecimal amount, Unit unit) {
    }

    public static Optional<ParsedAmount> parse(String title) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        Matcher m = UNIT_PATTERN.matcher(title);
        if (!m.find()) {
            return Optional.empty();
        }

        BigDecimal value;
        try {
            value = new BigDecimal(m.group(1).replace(',', '.'));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (value.signum() <= 0) {
            return Optional.empty();
        }

        return switch (m.group(2).toLowerCase()) {
            case "kg" -> Optional.of(new ParsedAmount(value.multiply(THOUSAND), Unit.G));
            case "g" -> Optional.of(new ParsedAmount(value, Unit.G));
            case "mg" -> Optional.of(new ParsedAmount(value.divide(THOUSAND), Unit.G));
            case "l" -> Optional.of(new ParsedAmount(value.multiply(THOUSAND), Unit.ML));
            case "ml" -> Optional.of(new ParsedAmount(value, Unit.ML));
            default -> Optional.empty();
        };
    }

    private UnitParser() {
    }
}
