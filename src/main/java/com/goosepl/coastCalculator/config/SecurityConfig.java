package com.goosepl.coastCalculator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // 정적 / 인증 공개
                        .requestMatchers("/", "/login", "/signup", "/css/**", "/js/**", "/error", "/favicon.ico").permitAll()
                        // 관리자 전용
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // 레시피: 작성/내 레시피/편집은 인증 필요 (구체적 패턴이 먼저 매칭됨)
                        .requestMatchers(HttpMethod.GET, "/recipes", "/recipes/new").authenticated()
                        .requestMatchers(HttpMethod.GET, "/recipes/*/edit").authenticated()
                        .requestMatchers(HttpMethod.POST, "/recipes/**").authenticated()
                        // 레시피 상세 조회는 익명 공개
                        .requestMatchers(HttpMethod.GET, "/recipes/*").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/")
                        .permitAll()
                );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
