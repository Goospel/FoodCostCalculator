package com.goosepl.coastCalculator.external.naver.dto;

import java.util.List;

public record NaverShopApiResponse(
        int total,
        int start,
        int display,
        List<NaverShopApiItem> items
) {
    public record NaverShopApiItem(
            String title,
            String link,
            String image,
            String lprice,
            String hprice,
            String mallName,
            String productId,
            String productType,
            String brand,
            String maker
    ) {
    }
}
