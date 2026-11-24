package mycomputer.project.ai_assistant.core.tools.filesystem;

import lombok.Getter;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;


@Service
public class WorkspaceSecurityService {

    // Lưu trữ workspace theo luồng của từng request
    private final ThreadLocal<Path> currentWorkspace = new ThreadLocal<>();

    public void setWorkspace(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            throw new IllegalArgumentException("Đường dẫn workspace không được để trống.");
        }

        Path target = mapHostPathToContainer(rawPath);
        try {
            if (!Files.exists(target)) {
                Files.createDirectories(target);
            }
            this.currentWorkspace.set(target);
        } catch (IOException e) {
            throw new IllegalStateException("Không thể khởi tạo thư mục workspace tại: " + target, e);
        }
    }

    //Dọn dẹp workspace khi request kết thúc
    public void clearWorkspace(){
        this.currentWorkspace.remove();
    }


    // Lấy thư mục gốc hiện tại. BẮT BUỘC phải được set trước, không có giá trị mặc định.
    public Path getRoot() {
        Path root = currentWorkspace.get();
        if (root == null) {
            throw new IllegalStateException("Thư mục làm việc (workspace) chưa được thiết lập. Vui lòng cung cấp 'workspacePath'.");
        }
        return root;
    }

    public Path resolve(String relative) {
        Path root = getRoot(); // Ném lỗi nếu chưa có workspace
        if (relative == null || relative.isBlank() || ".".equals(relative.trim())) {
            return root;
        }
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
        Path root = getRoot();
        Path normalized = path.toAbsolutePath().normalize();
        if (normalized.startsWith(root)) {
            return root.relativize(normalized).toString().replace('\\', '/');
        }
        return normalized.toString().replace('\\', '/');
    }

    // Ánh xạ đường dẫn Windowns -> Linux Mount trong Docker
    public Path mapHostPathToContainer(String rawPath) {
        String cleaned = rawPath.trim().replace('\\', '/');
        boolean isRunningInLinux = !System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

        // Nếu chạy trong container Linux mà nhận được đường dẫn Windows "D:/..." -> map thành "/mnt/d/..."
        if (isRunningInLinux && cleaned.matches("^[a-zA-Z]:/.*")) {
            char driveLetter = Character.toLowerCase(cleaned.charAt(0));
            String subPath = cleaned.substring(3);
            cleaned = "/mnt/" + driveLetter + "/" + subPath;
        }

        return Path.of(cleaned).toAbsolutePath().normalize();
    }
}
