package com.goosepl.coastCalculator.domain.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * 부팅 시 admin 계정을 1회 시드한다 (멱등). 비밀번호는 다음 우선순위로 결정:
 *  1) {@code app.admin.initial-password} (환경변수 {@code INITIAL_ADMIN_PASSWORD})
 *  2) 비어 있으면 SecureRandom으로 16자 랜덤 생성 → 로그에 1회 WARN으로 노출
 *
 * 이미 admin 계정이 존재하면 비밀번호를 덮어쓰지 않는다 (운영 중 변경 가능성 보존).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final int RANDOM_PASSWORD_LENGTH = 16;
    private static final String RANDOM_CHARSET =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:admin}")
    private String adminUsername;

    @Value("${app.admin.initial-password:}")
    private String configuredInitialPassword;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(adminUsername)) {
            log.info("admin 계정이 이미 존재합니다 (username={}). 시드 skip.", adminUsername);
            return;
        }

        String rawPassword;
        boolean generated = false;
        if (configuredInitialPassword == null || configuredInitialPassword.isBlank()) {
            rawPassword = generateRandomPassword();
            generated = true;
        } else {
            rawPassword = configuredInitialPassword;
        }

        User admin = User.builder()
                .username(adminUsername)
                .password(passwordEncoder.encode(rawPassword))
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);

        if (generated) {
            log.warn("");
            log.warn("=============================================================");
            log.warn(" INITIAL_ADMIN_PASSWORD 환경변수가 설정되지 않았습니다.");
            log.warn(" 랜덤 비밀번호를 생성했습니다 — 지금 복사해서 보관하세요.");
            log.warn(" username: {}", adminUsername);
            log.warn(" password: {}", rawPassword);
            log.warn(" 이 로그는 부팅 시 1회만 출력됩니다.");
            log.warn("=============================================================");
            log.warn("");
        } else {
            log.info("admin 계정을 생성했습니다 (username={}, role=ADMIN, 비밀번호는 설정값 사용)", adminUsername);
        }
    }

    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(RANDOM_PASSWORD_LENGTH);
        for (int i = 0; i < RANDOM_PASSWORD_LENGTH; i++) {
            sb.append(RANDOM_CHARSET.charAt(RANDOM.nextInt(RANDOM_CHARSET.length())));
        }
        return sb.toString();
    }
}
