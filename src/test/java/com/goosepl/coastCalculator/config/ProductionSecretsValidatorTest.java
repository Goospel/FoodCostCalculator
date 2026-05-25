package com.goosepl.coastCalculator.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * T1-4: 운영 시크릿 검증기 단위 테스트.
 *
 * Spring 컨텍스트 없이 생성자 직접 호출 — 검증 룰 자체에 집중.
 */
class ProductionSecretsValidatorTest {

    private static final String STRONG_ADMIN_PW = "k8Q!fLm2pZ@nB7w$";
    private static final String NON_DEFAULT_DB_USER = "prod_app";
    private static final String STRONG_DB_PW = "Xa9!mQ2$pR7nL4kW@vT8";
    private static final String NAVER_ID = "real-naver-id";
    private static final String NAVER_SECRET = "real-naver-secret";

    @Nested
    @DisplayName("모든 시크릿 정상 → 통과")
    class HappyPath {

        @Test
        @DisplayName("mock-enabled=true + 강한 DB/admin → 통과")
        void validatesOkWithMockEnabled() {
            ProductionSecretsValidator v = new ProductionSecretsValidator(
                    NON_DEFAULT_DB_USER, STRONG_DB_PW, STRONG_ADMIN_PW,
                    true, "", "");

            assertThat(v.collectViolations()).isEmpty();
            v.validate(); // no throw
        }

        @Test
        @DisplayName("mock-enabled=false + 모든 시크릿 명시 → 통과")
        void validatesOkWithRealNaver() {
            ProductionSecretsValidator v = new ProductionSecretsValidator(
                    NON_DEFAULT_DB_USER, STRONG_DB_PW, STRONG_ADMIN_PW,
                    false, NAVER_ID, NAVER_SECRET);

            assertThat(v.collectViolations()).isEmpty();
            v.validate();
        }
    }

    @Nested
    @DisplayName("DB 자격증명 검증")
    class DbCredentials {

        @Test
        @DisplayName("디폴트 coast/coastpass 그대로 → 거부")
        void rejectsDefaultDbCredentials() {
            ProductionSecretsValidator v = new ProductionSecretsValidator(
                    "coast", "coastpass", STRONG_ADMIN_PW,
                    true, "", "");

            List<String> violations = v.collectViolations();
            assertThat(violations).hasSize(1);
            assertThat(violations.get(0)).contains("DB 자격증명").contains("디폴트");
        }

        @Test
        @DisplayName("username만 디폴트(coast) + password 다름 → 통과 (둘 다 디폴트일 때만 위험)")
        void allowsDefaultUsernameWithDifferentPassword() {
            ProductionSecretsValidator v = new ProductionSecretsValidator(
                    "coast", STRONG_DB_PW, STRONG_ADMIN_PW,
                    true, "", "");

            assertThat(v.collectViolations()).isEmpty();
        }
    }

    @Nested
    @DisplayName("admin 비번 검증")
    class AdminPassword {

        @Test
        @DisplayName("admin 비번 비어있음 → 거부")
        void rejectsBlankAdminPassword() {
            ProductionSecretsValidator v = new ProductionSecretsValidator(
                    NON_DEFAULT_DB_USER, STRONG_DB_PW, "",
                    true, "", "");

            List<String> violations = v.collectViolations();
            assertThat(violations).anyMatch(s -> s.contains("INITIAL_ADMIN_PASSWORD"));
        }

        @Test
        @DisplayName("admin 비번 공백만 → 거부")
        void rejectsWhitespaceOnlyAdminPassword() {
            ProductionSecretsValidator v = new ProductionSecretsValidator(
                    NON_DEFAULT_DB_USER, STRONG_DB_PW, "   ",
                    true, "", "");

            List<String> violations = v.collectViolations();
            assertThat(violations).anyMatch(s -> s.contains("INITIAL_ADMIN_PASSWORD"));
        }

        @Test
        @DisplayName("admin 비번 null → 거부")
        void rejectsNullAdminPassword() {
            ProductionSecretsValidator v = new ProductionSecretsValidator(
                    NON_DEFAULT_DB_USER, STRONG_DB_PW, null,
                    true, "", "");

            List<String> violations = v.collectViolations();
            assertThat(violations).anyMatch(s -> s.contains("INITIAL_ADMIN_PASSWORD"));
        }
    }

    @Nested
    @DisplayName("Naver 키 검증")
    class NaverCredentials {

        @Test
        @DisplayName("mock-enabled=true이면 Naver 키 빈 채로도 통과")
        void mockEnabledIgnoresNaverKeys() {
            ProductionSecretsValidator v = new ProductionSecretsValidator(
                    NON_DEFAULT_DB_USER, STRONG_DB_PW, STRONG_ADMIN_PW,
                    true, "", "");

            assertThat(v.collectViolations()).isEmpty();
        }

        @Test
        @DisplayName("mock-enabled=false인데 client-id 비어있음 → 거부")
        void rejectsBlankClientIdWhenMockOff() {
            ProductionSecretsValidator v = new ProductionSecretsValidator(
                    NON_DEFAULT_DB_USER, STRONG_DB_PW, STRONG_ADMIN_PW,
                    false, "", NAVER_SECRET);

            List<String> violations = v.collectViolations();
            assertThat(violations).anyMatch(s -> s.contains("NAVER_CLIENT_ID"));
        }

        @Test
        @DisplayName("mock-enabled=false인데 client-secret 비어있음 → 거부")
        void rejectsBlankClientSecretWhenMockOff() {
            ProductionSecretsValidator v = new ProductionSecretsValidator(
                    NON_DEFAULT_DB_USER, STRONG_DB_PW, STRONG_ADMIN_PW,
                    false, NAVER_ID, "");

            List<String> violations = v.collectViolations();
            assertThat(violations).anyMatch(s -> s.contains("NAVER_CLIENT_SECRET"));
        }

        @Test
        @DisplayName("mock-enabled=false + 두 키 모두 비어있음 → 두 항목 모두 거부 (한 번에 표시)")
        void reportsAllNaverViolationsAtOnce() {
            ProductionSecretsValidator v = new ProductionSecretsValidator(
                    NON_DEFAULT_DB_USER, STRONG_DB_PW, STRONG_ADMIN_PW,
                    false, "", "");

            List<String> violations = v.collectViolations();
            assertThat(violations).hasSize(2);
            assertThat(violations).anyMatch(s -> s.contains("NAVER_CLIENT_ID"));
            assertThat(violations).anyMatch(s -> s.contains("NAVER_CLIENT_SECRET"));
        }
    }

    @Test
    @DisplayName("validate(): 위반이 하나라도 있으면 IllegalStateException + 모든 위반 메시지에 포함")
    void validateThrowsWithAllViolations() {
        // 세 항목 모두 위반
        ProductionSecretsValidator v = new ProductionSecretsValidator(
                "coast", "coastpass", "",
                false, "", "");

        assertThatThrownBy(v::validate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DB 자격증명")
                .hasMessageContaining("INITIAL_ADMIN_PASSWORD")
                .hasMessageContaining("NAVER_CLIENT_ID")
                .hasMessageContaining("NAVER_CLIENT_SECRET")
                .hasMessageContaining("부팅 중단");
    }
}
