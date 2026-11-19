package mycomputer.project.ai_assistant.core.tools.filesystem;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class WorkspaceSecurityService {

    private final Path root;

    public WorkspaceSecurityService(@Value("${app.agents.workspace:./workspace}") String workspace) {
        if (workspace == null || workspace.isBlank()) {
            workspace = "./workspace";
        }
        this.root = Path.of(workspace).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể khởi tạo thư mục workspace: " + this.root, e);
        }
    }

    public Path getRoot() {
        return root;
    }

    public Path resolve(String relative) {
        if (relative == null || relative.isBlank() || ".".equals(relative.trim())) {
            return root;
        }
        // Chuẩn hóa đường dẫn tương đối (bỏ dấu gạch chéo đầu nếu có)
        String cleaned = relative.trim().replace('\\', '/');
        while (cleaned.startsWith("/")) {
            cleaned = cleaned.substring(1);
        }
        Path p = root.resolve(cleaned).normalize();
        if (!p.startsWith(root)) {
            throw new SecurityException("Truy cập ngoài workspace bị chặn: " + relative);
        }
        return p;
    }

    public String relativize(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(root)) {
            return root.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString().replace('\\', '/');
    }
}
