package mycomputer.project.ai_assistant.core.tools.project;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mycomputer.project.ai_assistant.core.tools.filesystem.WorkspaceSecurityService;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectAnalysisService {

    private final WorkspaceSecurityService securityService;

    public String getProjectTree(String subDir, int maxDepth) {
        try {
            Path targetDir = securityService.resolve(subDir);
            if (!Files.exists(targetDir)) {
                return "Thư mục không tồn tại: " + subDir;
            }
            if (!Files.isDirectory(targetDir)) {
                return "'" + subDir + "' không phải là thư mục.";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Cấu trúc dự án: ").append(securityService.relativize(targetDir)).append("/\n");
            buildTree(targetDir, "", sb, 1, Math.max(1, Math.min(maxDepth, 6)));
            return sb.toString();
        } catch (Exception e) {
            log.error("Lỗi khi đọc cấu trúc thư mục {}", subDir, e);
            return "Lỗi khi đọc cấu trúc: " + e.getMessage();
        }
    }

    public String detectProjectType(String subDir) {
        Path targetDir = securityService.resolve(subDir);
        if (!Files.exists(targetDir) || !Files.isDirectory(targetDir)) {
            return "Không xác định: Thư mục không tồn tại.";
        }

        List<String> types = new ArrayList<>();
        if (Files.exists(targetDir.resolve("pom.xml"))) {
            types.add("Java Maven Project");
        }
        if (Files.exists(targetDir.resolve("build.gradle")) || Files.exists(targetDir.resolve("build.gradle.kts"))) {
            types.add("Java/Kotlin Gradle Project");
        }
        if (Files.exists(targetDir.resolve("requirements.txt")) || Files.exists(targetDir.resolve("pyproject.toml"))
                || Files.exists(targetDir.resolve("setup.py"))) {
            types.add("Python Project");
        }
        if (Files.exists(targetDir.resolve("package.json"))) {
            types.add("Node.js / JavaScript / TypeScript Project");
        }
        if (Files.exists(targetDir.resolve("Cargo.toml"))) {
            types.add("Rust Project");
        }
        if (Files.exists(targetDir.resolve("go.mod"))) {
            types.add("Go Project");
        }

        if (types.isEmpty()) {
            return "Dự án độc lập / Tập tin mã nguồn đơn lẻ (Standalone Scripts/Classes).";
        }
        return "Loại dự án phát hiện được: " + String.join(", ", types);
    }

    public String searchInFiles(String query, String fileExtension) {
        if (query == null || query.isBlank()) {
            return "Từ khóa tìm kiếm không được rỗng.";
        }
        Path root = securityService.getRoot();
        StringBuilder sb = new StringBuilder();
        sb.append("Kết quả tìm kiếm cho '").append(query).append("':\n");

        int count = 0;
        try (Stream<Path> stream = Files.walk(root, 10)) {
            List<Path> files = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> fileExtension == null || fileExtension.isBlank() || p.getFileName().toString().endsWith(fileExtension))
                    .filter(p -> !p.toString().contains(".git") && !p.toString().contains("target") && !p.toString().contains("node_modules"))
                    .toList();

            for (Path file : files) {
                try {
                    List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                    for (int i = 0; i < lines.size(); i++) {
                        String line = lines.get(i);
                        if (line.contains(query)) {
                            count++;
                            sb.append(securityService.relativize(file))
                                    .append(": dòng ").append(i + 1).append(": ")
                                    .append(line.trim()).append("\n");
                            if (count >= 50) {
                                sb.append("... [Đạt giới hạn 50 kết quả tìm kiếm] ...\n");
                                return sb.toString();
                            }
                        }
                    }
                } catch (Exception ignored) {
                    // bỏ qua file binary
                }
            }
        } catch (IOException e) {
            return "Lỗi khi tìm kiếm: " + e.getMessage();
        }

        if (count == 0) {
            return "Không tìm thấy nội dung '" + query + "' trong các tệp tin của workspace.";
        }
        return sb.toString();
    }

    private void buildTree(Path dir, String indent, StringBuilder sb, int currentDepth, int maxDepth) {
        if (currentDepth > maxDepth) return;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            List<Path> entries = new ArrayList<>();
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (!name.startsWith(".") && !"target".equals(name) && !"node_modules".equals(name) && !"__pycache__".equals(name)) {
                    entries.add(p);
                }
            }
            entries.sort((a, b) -> {
                boolean aDir = Files.isDirectory(a);
                boolean bDir = Files.isDirectory(b);
                if (aDir != bDir) return aDir ? -1 : 1;
                return a.getFileName().compareTo(b.getFileName());
            });

            for (int i = 0; i < entries.size(); i++) {
                Path entry = entries.get(i);
                boolean isLast = (i == entries.size() - 1);
                String prefix = isLast ? "└── " : "├── ";
                String childIndent = indent + (isLast ? "    " : "│   ");

                sb.append(indent).append(prefix).append(entry.getFileName().toString());
                if (Files.isDirectory(entry)) {
                    sb.append("/\n");
                    buildTree(entry, childIndent, sb, currentDepth + 1, maxDepth);
                } else {
                    try {
                        sb.append(" (").append(Files.size(entry)).append(" bytes)\n");
                    } catch (IOException e) {
                        sb.append("\n");
                    }
                }
            }
        } catch (IOException ignored) {
        }
    }
}
