package mycomputer.project.ai_assistant.core.tools.filesystem;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileSystemTool {

    private final FileSystemService fileSystemService;

    @Tool(description = "Tạo mới hoặc ghi đè toàn bộ một tệp tin trong workspace. BẮT BUỘC DÙNG khi tạo file mới hoặc khi viết lại mã nguồn hoàn chỉnh của một file (như viết thuật toán mới, cấu trúc lại class).")
    public String createFile(
            @ToolParam(description = "Đường dẫn tương đối tới file trong workspace (ví dụ: 'Solution.java' hoặc 'src/main/java/Graph.java')") String path,
            @ToolParam(description = "Nội dung đầy đủ của file mã nguồn hoặc cấu hình cần ghi") String content) {
        return fileSystemService.create(path, content);
    }

    @Tool(description = "Đọc nội dung văn bản của một tệp tin trong workspace.")
    public String readFile(
            @ToolParam(description = "Đường dẫn tương đối tới file cần đọc (ví dụ: 'Solution.java' hoặc 'pom.xml')") String path) {
        return fileSystemService.read(path);
    }

    @Tool(description = "Sửa đổi một vài dòng nhỏ trong file. TUYỆT ĐỐI KHÔNG dùng nếu replacementContent chứa cả một class hoàn chỉnh hoặc import (hãy dùng createFile để ghi đè thay thế). targetContent phải là đoạn mã cũ chính xác 100%.")
    public String updateFile(
            @ToolParam(description = "Đường dẫn tương đối tới file cần cập nhật") String path,
            @ToolParam(description = "Đoạn văn bản/mã nguồn cũ chính xác cần thay thế") String targetContent,
            @ToolParam(description = "Đoạn văn bản/mã nguồn mới thay thế vào") String replacementContent) {
        return fileSystemService.edit(path, targetContent, replacementContent);
    }

    @Tool(description = "Đổi tên hoặc di chuyển tệp tin/thư mục trong workspace từ đường dẫn cũ sang đường dẫn mới. Tự động đồng bộ tên class nếu là file Java. BẮT BUỘC DÙNG khi người dùng yêu cầu đổi tên file (rename).")
    public String renameFile(
            @ToolParam(description = "Đường dẫn tương đối tới file cũ (ví dụ: 'Hello.java' hoặc 'demo/Hello.java')") String oldPath,
            @ToolParam(description = "Đường dẫn tương đối tới file mới (ví dụ: 'Algorithm.java' hoặc 'demo/Algorithm.java')") String newPath) {
        return fileSystemService.rename(oldPath, newPath);
    }

    @Tool(description = "Xóa một tệp tin hoặc thư mục con trong workspace.")
    public String deleteFile(
            @ToolParam(description = "Đường dẫn tương đối tới file hoặc thư mục cần xóa") String path) {
        return fileSystemService.delete(path);
    }

    @Tool(description = "Liệt kê danh sách các tệp tin và thư mục con trong thư mục chỉ định thuộc workspace.")
    public String listFiles(
            @ToolParam(description = "Thư mục tương đối cần xem (để trống hoặc '.' để xem thư mục gốc workspace)") String subDirectory) {
        return fileSystemService.list(subDirectory);
    }
}
