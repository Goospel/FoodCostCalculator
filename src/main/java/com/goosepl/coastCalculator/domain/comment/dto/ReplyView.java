package com.goosepl.coastCalculator.domain.comment.dto;

import java.time.LocalDateTime;

/**
 * 대댓글(1단계) 표시용 DTO.
 */
public record ReplyView(
        Long id,
        String username,
        String content,
        LocalDateTime createdAt
) {
}
