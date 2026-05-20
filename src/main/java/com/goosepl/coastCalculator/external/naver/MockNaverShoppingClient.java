package com.goosepl.coastCalculator.external.naver;

import com.goosepl.coastCalculator.external.naver.dto.NaverProduct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

@Slf4j
@Component
@ConditionalOnProperty(name = "naver.api.mock-enabled", havingValue = "true", matchIfMissing = true)
public class MockNaverShoppingClient implements NaverShoppingClient {

    private static final List<NaverProduct> SAMPLE = List.of(
            new NaverProduct("MOCK-1001", "오뚜기 다목적 밀가루 1kg", 2500,
                    "https://example.com/img1.jpg", "오뚜기몰", "https://example.com/p1"),
            new NaverProduct("MOCK-1002", "CJ 백설 다목적 밀가루 1kg", 2800,
                    "https://example.com/img2.jpg", "CJ더마켓", "https://example.com/p2"),
            new NaverProduct("MOCK-1003", "대한제분 곰표 다목적 밀가루 20kg", 30000,
                    "https://example.com/img3.jpg", "대한제분", "https://example.com/p3"),
            new NaverProduct("MOCK-2001", "백설 하얀설탕 1kg", 2200,
                    "https://example.com/img4.jpg", "CJ더마켓", "https://example.com/p4"),
            new NaverProduct("MOCK-2002", "백설 하얀설탕 3kg", 6500,
                    "https://example.com/img5.jpg", "CJ더마켓", "https://example.com/p5"),
            new NaverProduct("MOCK-3001", "청정원 천일염 500g", 1500,
                    "https://example.com/img6.jpg", "대상몰", "https://example.com/p6"),
            new NaverProduct("MOCK-3002", "히말라야 핑크소금 1kg", 8900,
                    "https://example.com/img7.jpg", "오가닉마켓", "https://example.com/p7"),
            new NaverProduct("MOCK-4001", "샘표 양조간장 1.5L", 5500,
                    "https://example.com/img8.jpg", "샘표몰", "https://example.com/p8"),
            new NaverProduct("MOCK-4002", "오뚜기 진간장 500ml", 3200,
                    "https://example.com/img9.jpg", "오뚜기몰", "https://example.com/p9"),
            new NaverProduct("MOCK-5001", "백설 식용유 카놀라 900ml", 4800,
                    "https://example.com/img10.jpg", "CJ더마켓", "https://example.com/p10")
    );

    @Override
    public List<NaverProduct> search(String keyword) {
        log.info("[Mock] Naver 검색 호출: keyword={}", keyword);
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        String lowered = keyword.toLowerCase(Locale.ROOT);
        return SAMPLE.stream()
                .filter(p -> p.title().toLowerCase(Locale.ROOT).contains(lowered))
                .toList();
    }
}
