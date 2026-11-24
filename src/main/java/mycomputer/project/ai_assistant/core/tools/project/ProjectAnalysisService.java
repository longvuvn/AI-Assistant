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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
            buildTree(targetDir, "", sb, 1, Math.max(1, Math.min(maxDepth, 12)));
            return sb.toString();
        } catch (Exception e) {
            log.error("Lỗi khi đọc cấu trúc thư mục {}", subDir, e);
            return "Lỗi khi đọc cấu trúc: " + e.getMessage();
        }
    }

    public String analyzeSpringBootProject(String subDir) {
        try {
            Path projectDir = securityService.resolve(subDir);
            if (!Files.exists(projectDir) || !Files.isDirectory(projectDir)) {
                return null;
            }

            Path pomPath = projectDir.resolve("pom.xml");
            Path buildGradlePath = projectDir.resolve("build.gradle");
            if (!Files.exists(pomPath) && !Files.exists(buildGradlePath)) {
                return null;
            }

            // 1. Quét tìm class chính có chứa @SpringBootApplication
            Path srcMainJava = projectDir.resolve("src/main/java");
            if (!Files.exists(srcMainJava) || !Files.isDirectory(srcMainJava)) {
                return null;
            }

            Path mainAppFile = null;
            String basePackage = null;
            try (Stream<Path> walk = Files.walk(srcMainJava, 10)) {
                List<Path> javaFiles = walk.filter(p -> p.toString().endsWith(".java")).toList();
                for (Path jf : javaFiles) {
                    String content = Files.readString(jf, StandardCharsets.UTF_8);
                    if (content.contains("@SpringBootApplication")) {
                        mainAppFile = jf;
                        Matcher pm = Pattern.compile("package\\s+([a-zA-Z0-9_.]+);").matcher(content);
                        if (pm.find()) {
                            basePackage = pm.group(1);
                        }
                        break;
                    }
                }
            }

            if (mainAppFile == null || basePackage == null) {
                return null;
            }

            // 2. Đọc pom.xml để phát hiện các dependency có sẵn
            StringBuilder deps = new StringBuilder();
            if (Files.exists(pomPath)) {
                String pomContent = Files.readString(pomPath, StandardCharsets.UTF_8);
                if (pomContent.contains("spring-boot-starter-web")) deps.append("Spring Web (REST API), ");
                if (pomContent.contains("spring-boot-starter-data-jpa")) deps.append("Spring Data JPA, ");
                if (pomContent.contains("lombok")) deps.append("Lombok, ");
                if (pomContent.contains("h2")) deps.append("H2 Database, ");
                if (pomContent.contains("mysql")) deps.append("MySQL Driver, ");
                if (pomContent.contains("postgresql")) deps.append("PostgreSQL Driver, ");
                if (pomContent.contains("spring-boot-starter-validation")) deps.append("Jakarta Validation, ");
            }

            // 3. Kiểm tra file cấu hình application.properties hoặc application.yaml
            Path resourcesDir = projectDir.resolve("src/main/resources");
            Path appYaml = resourcesDir.resolve("application.yaml");
            Path appYml = resourcesDir.resolve("application.yml");
            Path appProperties = resourcesDir.resolve("application.properties");

            String appConfigRelPath = "src/main/resources/application.properties";
            boolean isYaml = false;
            if (Files.exists(appYaml)) {
                appConfigRelPath = "src/main/resources/application.yaml";
                isYaml = true;
            } else if (Files.exists(appYml)) {
                appConfigRelPath = "src/main/resources/application.yml";
                isYaml = true;
            } else if (Files.exists(appProperties)) {
                appConfigRelPath = "src/main/resources/application.properties";
                isYaml = false;
            }

            String relSrcMainJava = securityService.relativize(srcMainJava);
            String packageRelDir = basePackage.replace('.', '/');
            String basePackageRelPath = relSrcMainJava + "/" + packageRelDir;

            StringBuilder sb = new StringBuilder();
            sb.append("\n=== THÔNG TIN DỰ ÁN SPRING BOOT ĐÃ TỒN TẠI (SPRING INITIALIZR) ===\n")
                    .append("- Thư mục gốc dự án: ").append(securityService.relativize(projectDir)).append("\n")
                    .append("- File pom/gradle: ").append(Files.exists(pomPath) ? securityService.relativize(pomPath) : securityService.relativize(buildGradlePath)).append("\n")
                    .append("- File cấu hình ứng dụng: ").append(appConfigRelPath).append("\n")
                    .append("- Class khởi chạy chính (@SpringBootApplication): ").append(mainAppFile.getFileName()).append("\n")
                    .append("- Package gốc của dự án: ").append(basePackage).append("\n")
                    .append("- Thư mục package gốc mã nguồn: ").append(basePackageRelPath).append("\n");
            if (!deps.isEmpty()) {
                sb.append("- Thư viện đã có trong dự án: ").append(deps.substring(0, deps.length() - 2)).append("\n");
            }
            sb.append("\nQUY TẮC BẮT BUỘC ĐỂ CODE TRỰC TIẾP TRÊN DỰ ÁN NÀY:\n")
                    .append("1. KHÔNG TẠO LẠI DỰ ÁN MỚI. Giữ nguyên cấu trúc thư mục hiện có.\n")
                    .append("2. CẤU HÌNH CƠ SỞ DỮ LIỆU (DATABASE CONFIGURATION):\n")
                    .append("   - KHI NGƯỜI DÙNG YÊU CẦU KẾT NỐI DATABASE (MySQL, PostgreSQL, v.v.):\n")
                    .append("     BẮT BUỘC phải tạo hoặc cập nhật file cấu hình `").append(appConfigRelPath).append("` với cấu hình datasource đầy đủ.\n")
                    .append("     Ví dụ cấu hình MySQL trong `").append(appConfigRelPath).append("`:\n");
            if (isYaml) {
                sb.append("     ```yaml\n")
                        .append("     spring:\n")
                        .append("       datasource:\n")
                        .append("         url: jdbc:mysql://localhost:3306/student_management?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC\n")
                        .append("         username: root\n")
                        .append("         password: root\n")
                        .append("         driver-class-name: com.mysql.cj.jdbc.Driver\n")
                        .append("       jpa:\n")
                        .append("         hibernate:\n")
                        .append("           ddl-auto: update\n")
                        .append("         show-sql: true\n")
                        .append("     ```\n");
            } else {
                sb.append("     ```properties\n")
                        .append("     spring.datasource.url=jdbc:mysql://localhost:3306/student_management?createDatabaseIfNotExist=true&useSSL=false&serverTimezone=UTC\n")
                        .append("     spring.datasource.username=root\n")
                        .append("     spring.datasource.password=root\n")
                        .append("     spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver\n")
                        .append("     spring.jpa.hibernate.ddl-auto=update\n")
                        .append("     spring.jpa.show-sql=true\n")
                        .append("     ```\n");
            }
            sb.append("   - Nếu `pom.xml` chưa có driver tương ứng (ví dụ `com.mysql:mysql-connector-j`), hãy thêm dependency vào `pom.xml`.\n")
                    .append("3. MÃ NGUỒN CÁC TẦNG CHUẨN (BẮT BUỘC IMPORT ĐẦY ĐỦ CÁC ANNOTATION):\n")
                    .append("   - Tầng Entity: '").append(basePackageRelPath).append("/model/[Tên].java' (BẮT BUỘC dùng 'jakarta.persistence.*', TUYỆT ĐỐI KHÔNG dùng 'javax.*').\n")
                    .append("   - Tầng Repository: '").append(basePackageRelPath).append("/repository/[Tên]Repository.java' (kèm 'import org.springframework.stereotype.Repository;' và extends JpaRepository).\n")
                    .append("   - Tầng Service: '").append(basePackageRelPath).append("/service/[Tên]Service.java' (BẮT BUỘC PHẢI CÓ 'import org.springframework.stereotype.Service;' và annotation @Service).\n")
                    .append("   - Tầng Controller: '").append(basePackageRelPath).append("/controller/[Tên]Controller.java' (BẮT BUỘC PHẢI CÓ 'import org.springframework.web.bind.annotation.*;' và annotation @RestController, @RequestMapping).\n")
                    .append("4. TỰ ĐỘNG LƯU: Hãy xuất ra toàn bộ mã nguồn của các file cần thiết. Đối với mỗi file, hãy ghi rõ đường dẫn tương đối trong dấu backtick (ví dụ: `").append(appConfigRelPath).append("`, `").append(basePackageRelPath).append("/model/User.java`) kèm theo khối mã nguồn hoàn chỉnh ```java ... ``` hoặc ```properties ... ``` / ```yaml ... ``` tương ứng. Hệ thống sẽ tự động quét và lưu toàn bộ vào dự án.\n")
                    .append("==================================================================\n");

            return sb.toString();
        } catch (Exception e) {
            log.warn("Lỗi khi phân tích Spring Boot project: {}", e.getMessage());
            return null;
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
