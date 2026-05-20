package com.goosepl.coastCalculator.domain.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_INITIAL_PASSWORD = "admin123!!";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByUsername(ADMIN_USERNAME)) {
            log.info("admin 계정이 이미 존재합니다. 시드 skip.");
            return;
        }
        User admin = User.builder()
                .username(ADMIN_USERNAME)
                .password(passwordEncoder.encode(ADMIN_INITIAL_PASSWORD))
                .role(Role.ADMIN)
                .build();
        userRepository.save(admin);
        log.info("admin 계정을 생성했습니다 (username={}, role=ADMIN)", ADMIN_USERNAME);
    }
}
