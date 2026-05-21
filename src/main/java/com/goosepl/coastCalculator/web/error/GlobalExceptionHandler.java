package com.goosepl.coastCalculator.web.error;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 전역 예외 처리.
 *
 * <p>각 컨트롤러에서 개별적으로 처리하지 않은 예외를 일관된 상태 코드 + 사용자 친화적 페이지로 매핑합니다.
 * 컨트롤러 내 `try/catch` 블록이 먼저 동작하므로, 이미 잡고 있는 흐름은 영향받지 않습니다.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public String handleAccessDenied(AccessDeniedException e, HttpServletRequest req) {
        log.warn("[403] access denied: path={}, msg={}", req.getRequestURI(), e.getMessage());
        return "error/403";
    }

    @ExceptionHandler(NoResourceFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String handleNoResource(NoResourceFoundException e, HttpServletRequest req) {
        log.warn("[404] no resource: path={}", req.getRequestURI());
        return "error/404";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public String handleIllegalArgument(IllegalArgumentException e, Model model, HttpServletRequest req) {
        log.warn("[400] bad request: path={}, msg={}", req.getRequestURI(), e.getMessage());
        model.addAttribute("errorMessage", e.getMessage());
        model.addAttribute("status", 400);
        return "error/error";
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleIllegalState(IllegalStateException e, HttpServletRequest req) {
        log.error("[500] illegal state: path={}", req.getRequestURI(), e);
        return "error/500";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String handleGeneric(Exception e, HttpServletRequest req) {
        log.error("[500] unhandled exception: path={}", req.getRequestURI(), e);
        return "error/500";
    }
}
