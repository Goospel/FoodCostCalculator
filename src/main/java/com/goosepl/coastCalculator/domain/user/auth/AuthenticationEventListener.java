package com.goosepl.coastCalculator.domain.user.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AuthenticationFailureBadCredentialsEvent;
import org.springframework.security.authentication.event.AuthenticationFailureLockedEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.stereotype.Component;

/**
 * Spring Security 인증 이벤트를 구독해서 {@link LoginAttemptService}에 위임.
 *
 * - {@link AuthenticationFailureBadCredentialsEvent}: 비밀번호 오류 (카운터 +1)
 * - {@link AuthenticationSuccessEvent}: 로그인 성공 (카운터 리셋)
 * - {@link AuthenticationFailureLockedEvent}: 이미 잠긴 상태 진입 시도 — 카운터 변경 X (잠금 유지)
 *
 * Spring Boot가 기본으로 {@code DefaultAuthenticationEventPublisher}를 등록해주므로 별도 빈 설정 불필요.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthenticationEventListener {

    private final LoginAttemptService loginAttemptService;

    @EventListener
    public void onBadCredentials(AuthenticationFailureBadCredentialsEvent event) {
        String username = String.valueOf(event.getAuthentication().getPrincipal());
        loginAttemptService.recordFailure(username);
        log.debug("[Auth] 로그인 실패 카운터 +1: username={}", username);
    }

    @EventListener
    public void onSuccess(AuthenticationSuccessEvent event) {
        String username = event.getAuthentication().getName();
        loginAttemptService.recordSuccess(username);
        log.debug("[Auth] 로그인 성공으로 카운터 리셋: username={}", username);
    }
}
