package com.goosepl.coastCalculator.domain.comment.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 루트 댓글 + 자식 대댓글들을 묶어서 표시하기 위한 DTO.
 */
public record RootCommentView(
        Long id,
        String username,
        String content,
        LocalDateTime createdAt,
        List<ReplyView> replies
) {
}
