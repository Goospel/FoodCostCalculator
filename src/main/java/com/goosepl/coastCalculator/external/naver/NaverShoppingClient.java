package com.goosepl.coastCalculator.external.naver;

import com.goosepl.coastCalculator.external.naver.dto.NaverProduct;

import java.util.List;

public interface NaverShoppingClient {

    List<NaverProduct> search(String keyword);
}
