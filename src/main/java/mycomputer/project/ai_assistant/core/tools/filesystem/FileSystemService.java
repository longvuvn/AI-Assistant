package mycomputer.project.ai_assistant.core.tools.filesystem;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileSystemService {

    private final WorkspaceSecurityService securityService;
    private final Set<String> recentlyModifiedFiles = ConcurrentHashMap.newKeySet();

    public String create(String relativePath, String content) {
        if (relativePath == null || relativePath.isBlank()) {
            return "Lỗi: Đường dẫn file không được để trống.";
        }
        try {
            Path target = securityService.resolve(relativePath);
            if (Files.isDirectory(target)) {
                return "Lỗi: Đường dẫn '" + relativePath + "' là thư mục, không thể ghi như file.";
            }

            Path parent = target.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }

            String textContent = content == null ? "" : content;
            Files.writeString(target, textContent, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            String rel = securityService.relativize(target);
            recentlyModifiedFiles.add(rel);
            log.info("Created/Updated file: {}", rel);
            return "Thành công: Đã tạo/ghi file '" + rel + "' (" + textContent.length() + " ký tự).";
        } catch (Exception e) {
            log.error("Lỗi khi ghi file {}", relativePath, e);
            return "Lỗi khi ghi file '" + relativePath + "': " + e.getMessage();
        }
    }

    public String read(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "Lỗi: Đường dẫn file không được để trống.";
        }
        try {
            Path target = securityService.resolve(relativePath);
            if (!Files.exists(target)) {
                return "Lỗi: File '" + relativePath + "' không tồn tại trong workspace.";
            }
            if (Files.isDirectory(target)) {
                return "Lỗi: '" + relativePath + "' là thư mục, không phải file văn bản. Hãy dùng công cụ listFiles.";
            }
            return Files.readString(target, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Lỗi khi đọc file {}", relativePath, e);
            return "Lỗi khi đọc file '" + relativePath + "': " + e.getMessage();
        }
    }

    public String edit(String relativePath, String targetContent, String replacementContent) {
        if (relativePath == null || relativePath.isBlank()) {
            return "Lỗi: Đường dẫn file không được để trống.";
        }
        try {
            Path target = securityService.resolve(relativePath);
            if (!Files.exists(target)) {
                return "Lỗi: File '" + relativePath + "' không tồn tại.";
            }
            String current = Files.readString(target, StandardCharsets.UTF_8);
            if (!current.contains(targetContent)) {
                return "Lỗi: Không tìm thấy đoạn nội dung cần thay thế trong '" + relativePath + "'.";
            }
            String updated = current.replace(targetContent, replacementContent == null ? "" : replacementContent);
            Files.writeString(target, updated, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            String rel = securityService.relativize(target);
            recentlyModifiedFiles.add(rel);
            return "Thành công: Đã cập nhật file '" + rel + "'.";
        } catch (Exception e) {
            log.error("Lỗi khi sửa file {}", relativePath, e);
            return "Lỗi khi sửa file '" + relativePath + "': " + e.getMessage();
        }
    }

    public String delete(String relativePath) {
        if (relativePath == null || relativePath.isBlank()) {
            return "Lỗi: Đường dẫn file cần xóa không được để trống.";
        }
        try {
            Path target = securityService.resolve(relativePath);
            if (target.equals(securityService.getRoot())) {
                return "Lỗi: Không được phép xóa thư mục gốc workspace.";
            }
            if (!Files.exists(target)) {
                return "Thông báo: File hoặc thư mục '" + relativePath + "' không tồn tại.";
            }

            if (Files.isDirectory(target)) {
                deleteDirectoryRecursively(target);
            } else {
                Files.delete(target);
            }
            String rel = securityService.relativize(target);
            recentlyModifiedFiles.remove(rel);
            return "Thành công: Đã xóa '" + rel + "'.";
        } catch (Exception e) {
            log.error("Lỗi khi xóa {}", relativePath, e);
            return "Lỗi khi xóa '" + relativePath + "': " + e.getMessage();
        }
    }

    public String list(String relativeSubDir) {
        try {
            Path dir = securityService.resolve(relativeSubDir);
            if (!Files.exists(dir)) {
                return "Lỗi: Thư mục '" + relativeSubDir + "' không tồn tại.";
            }
            if (!Files.isDirectory(dir)) {
                return "Lỗi: '" + relativeSubDir + "' là file, không phải thư mục.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Danh sách tệp tin trong: ").append(securityService.relativize(dir)).append("/\n");
            try (Stream<Path> stream = Files.walk(dir, 2)) {
                List<Path> paths = stream
                        .filter(p -> !p.equals(dir))
                        .sorted(Comparator.comparing((Path p) -> !Files.isDirectory(p))
                                .thenComparing(Path::getFileName))
                        .limit(100)
                        .toList();

                if (paths.isEmpty()) {
                    sb.append("  (thư mục trống)\n");
                } else {
                    for (Path p : paths) {
                        String rel = securityService.relativize(p);
                        if (Files.isDirectory(p)) {
                            sb.append("  [DIR]  ").append(rel).append("/\n");
                        } else {
                            long size = Files.size(p);
                            sb.append("  [FILE] ").append(rel).append(" (").append(size).append(" bytes)\n");
                        }
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Lỗi khi liệt kê thư mục {}", relativeSubDir, e);
            return "Lỗi khi liệt kê thư mục: " + e.getMessage();
        }
    }

    public boolean exists(String relativePath) {
        try {
            Path target = securityService.resolve(relativePath);
            return Files.exists(target);
        } catch (Exception e) {
            return false;
        }
    }

    public List<String> getRecentlyModifiedFiles() {
        return new ArrayList<>(recentlyModifiedFiles);
    }

    public void clearRecentlyModifiedFiles() {
        recentlyModifiedFiles.clear();
    }

    private void deleteDirectoryRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }
}
