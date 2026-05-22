package com.goosepl.coastCalculator.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SignupRequest {

    @NotBlank(message = "아이디를 입력해주세요")
    @Size(min = 4, max = 50, message = "아이디는 4~50자여야 합니다")
    private String username;

    @NotBlank(message = "비밀번호를 입력해주세요")
    @Size(min = 8, max = 100, message = "비밀번호는 8자 이상이어야 합니다")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다"
    )
    private String password;

    @NotBlank(message = "비밀번호 확인을 입력해주세요")
    private String passwordConfirm;
}
