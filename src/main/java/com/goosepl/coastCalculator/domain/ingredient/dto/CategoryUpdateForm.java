package com.goosepl.coastCalculator.domain.ingredient.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CategoryUpdateForm {

    @Size(max = 100, message = "카테고리는 100자 이하여야 합니다")
    private String category;

    public CategoryUpdateForm(String category) {
        this.category = category;
    }
}
