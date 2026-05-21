package com.goosepl.coastCalculator.storage;

import com.goosepl.coastCalculator.config.StorageProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class LocalImageStorageService implements ImageStorageService {

    private static final long MAX_BYTES = 5L * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("jpg", "jpeg", "png", "webp");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp"
    );
    private static final String URL_PREFIX = "/uploads/recipes/";
    private static final String SUBDIR = "recipes";

    private final Path root;

    public LocalImageStorageService(StorageProperties properties) {
        this.root = Paths.get(properties.uploadDir()).toAbsolutePath().normalize();
    }

    @PostConstruct
    public void init() {
        try {
            Files.createDirectories(root.resolve(SUBDIR));
            log.info("[Storage] 이미지 업로드 디렉토리 준비됨: {}", root.resolve(SUBDIR));
        } catch (IOException e) {
            throw new IllegalStateException("업로드 디렉토리 생성 실패: " + root, e);
        }
    }

    @Override
    public String save(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("업로드된 파일이 비어있습니다");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("이미지는 5MB 이하만 업로드 가능합니다");
        }
        String ext = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new IllegalArgumentException("허용되지 않는 확장자입니다: " + ext + " (jpg/jpeg/png/webp만 가능)");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다: " + contentType);
        }

        String filename = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        Path target = root.resolve(SUBDIR).resolve(filename).normalize();

        // 디렉토리 트래버설 방지
        if (!target.startsWith(root.resolve(SUBDIR))) {
            throw new IllegalArgumentException("유효하지 않은 저장 경로");
        }

        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("이미지 저장 실패", e);
        }
        String url = URL_PREFIX + filename;
        log.debug("[Storage] 이미지 저장: {} -> {}", file.getOriginalFilename(), url);
        return url;
    }

    @Override
    public void delete(String url) {
        if (url == null || url.isBlank()) {
            return;
        }
        if (!url.startsWith(URL_PREFIX)) {
            log.warn("[Storage] 알 수 없는 URL 형식, 삭제 skip: {}", url);
            return;
        }
        String filename = url.substring(URL_PREFIX.length());
        // 경로 인젝션 방지
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            log.warn("[Storage] 의심스러운 파일명, 삭제 skip: {}", filename);
            return;
        }
        Path target = root.resolve(SUBDIR).resolve(filename).normalize();
        if (!target.startsWith(root.resolve(SUBDIR))) {
            log.warn("[Storage] 경로 이탈 시도, 삭제 skip: {}", target);
            return;
        }
        try {
            boolean deleted = Files.deleteIfExists(target);
            log.debug("[Storage] 이미지 삭제 {}: {}", deleted ? "성공" : "대상 없음", target);
        } catch (IOException e) {
            log.warn("[Storage] 이미지 삭제 실패: {}", target, e);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
