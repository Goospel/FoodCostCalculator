package com.goosepl.coastCalculator.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * T1-4: 운영 프로파일 부팅 시 필수 시크릿 검증.
 *
 * <p>{@code @Profile("prod")}이라 default/local/test에서는 미활성. 운영 부팅 시
 * 디폴트 placeholder 그대로면 즉시 {@link IllegalStateException}을 던져 부팅 거부.
 * 보안 사고 예방: 디폴트 비번으로 운영 노출 / 랜덤 비번 부팅 로그 유출 / Naver 키 누락 시 mock 응답 노출.</p>
 *
 * <h3>검증 룰</h3>
 * <ol>
 *   <li>DB 자격증명 = 디폴트({@code coast}/{@code coastpass}) 조합 거부</li>
 *   <li>{@code INITIAL_ADMIN_PASSWORD} 비어있으면 거부 (운영에서 랜덤 비번 로그 노출 금지 — 회전 어려움 + 로그 유출 시 즉시 침해)</li>
 *   <li>{@code naver.api.mock-enabled=false}인데 {@code client-id}/{@code client-secret}가 비어있으면 거부</li>
 * </ol>
 *
 * <p>검증 실패 시 모든 위반 사항을 한 번에 모아 사용자에게 보여줌(여러 환경변수 빠졌을 때 한 번에 파악).</p>
 *
 * <p>외부 저장소(Vault/AWS Secrets Manager) 연동은 의도적 비포함 — 이 검증기가 통과한 환경변수가
 * 어떤 경로(systemd EnvironmentFile/ECS Task Definition/K8s Secret/AWS SM)로 주입됐는지는 불문.</p>
 */
@Slf4j
@Component
@Profile("prod")
public class ProductionSecretsValidator {

    static final String DEFAULT_DB_USERNAME = "coast";
    static final String DEFAULT_DB_PASSWORD = "coastpass";

    private final String dbUsername;
    private final String dbPassword;
    private final String adminPassword;
    private final boolean mockEnabled;
    private final String naverClientId;
    private final String naverClientSecret;

    public ProductionSecretsValidator(
            @Value("${spring.datasource.username:}") String dbUsername,
            @Value("${spring.datasource.password:}") String dbPassword,
            @Value("${app.admin.initial-password:}") String adminPassword,
            @Value("${naver.api.mock-enabled:true}") boolean mockEnabled,
            @Value("${naver.api.client-id:}") String naverClientId,
            @Value("${naver.api.client-secret:}") String naverClientSecret) {
        this.dbUsername = dbUsername;
        this.dbPassword = dbPassword;
        this.adminPassword = adminPassword;
        this.mockEnabled = mockEnabled;
        this.naverClientId = naverClientId;
        this.naverClientSecret = naverClientSecret;
    }

    @PostConstruct
    public void validate() {
        List<String> violations = collectViolations();
        if (violations.isEmpty()) {
            log.info("운영 시크릿 검증 통과 — 안전하게 부팅 계속");
            return;
        }

        String msg = formatErrorMessage(violations);
        log.error(msg);
        throw new IllegalStateException(msg);
    }

    /** 검증 로직 분리 — 테스트 용이성. */
    List<String> collectViolations() {
        List<String> violations = new ArrayList<>();

        if (DEFAULT_DB_USERNAME.equals(dbUsername) && DEFAULT_DB_PASSWORD.equals(dbPassword)) {
            violations.add("DB 자격증명이 디폴트 (" + DEFAULT_DB_USERNAME + "/" + DEFAULT_DB_PASSWORD
                    + ") 그대로입니다. 환경변수 DB_USERNAME, DB_PASSWORD를 설정하세요.");
        }
        if (adminPassword == null || adminPassword.isBlank()) {
            violations.add("INITIAL_ADMIN_PASSWORD 환경변수가 비어 있습니다. "
                    + "운영에서는 랜덤 비번 부팅 로그 노출이 위험합니다 — 명시적으로 강한 비번을 주입하세요.");
        }
        if (!mockEnabled) {
            if (naverClientId == null || naverClientId.isBlank()) {
                violations.add("NAVER_CLIENT_ID 환경변수가 비어 있습니다 (mock-enabled=false). "
                        + "키를 발급받거나 Mock 모드를 다시 켜세요.");
            }
            if (naverClientSecret == null || naverClientSecret.isBlank()) {
                violations.add("NAVER_CLIENT_SECRET 환경변수가 비어 있습니다 (mock-enabled=false).");
            }
        }
        return violations;
    }

    private String formatErrorMessage(List<String> violations) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("=============================================================\n");
        sb.append("운영 시크릿 검증 실패 — 부팅 중단\n");
        sb.append("=============================================================\n");
        for (int i = 0; i < violations.size(); i++) {
            sb.append("  ").append(i + 1).append(". ").append(violations.get(i)).append("\n");
        }
        sb.append("=============================================================\n");
        sb.append("해결 가이드: docs/deployment.md § 시크릿 관리\n");
        sb.append("=============================================================\n");
        return sb.toString();
    }
}
