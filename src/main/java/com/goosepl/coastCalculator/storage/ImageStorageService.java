package com.goosepl.coastCalculator.storage;

import org.springframework.web.multipart.MultipartFile;

/**
 * 이미지 업로드 추상화. MVP는 로컬 파일시스템, 추후 S3 등으로 교체 가능.
 */
public interface ImageStorageService {

    /**
     * 파일을 저장하고 외부에서 접근 가능한 URL 경로를 반환한다.
     * 예: "/uploads/recipes/abc123.jpg"
     *
     * @param file 비어있지 않은 multipart 파일
     * @return 저장된 파일의 URL 경로 (DB에 그대로 저장 가능)
     * @throws IllegalArgumentException 검증 실패 시 (크기/확장자/빈 파일 등)
     */
    String save(MultipartFile file);

    /**
     * 저장된 파일을 삭제한다. null/빈 문자열/존재하지 않는 파일이면 조용히 무시.
     */
    void delete(String url);
}
