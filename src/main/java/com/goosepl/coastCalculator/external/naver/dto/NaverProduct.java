package com.goosepl.coastCalculator.external.naver.dto;

public record NaverProduct(
        String naverProductId,
        String title,
        int price,
        String image,
        String mallName,
        String link
) {
}
