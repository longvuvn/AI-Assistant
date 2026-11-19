package mycomputer.project.ai_assistant.core.tools.filesystem;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileSystemTool {

    private final FileSystemService fileSystemService;

    @Tool(description = "Tạo mới hoặc ghi đè một tệp tin trong workspace với nội dung chỉ định. Tự động tạo thư mục cha nếu chưa có.")
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

    @Tool(description = "Sửa đổi một phần nội dung trong tệp tin bằng cách thay thế đoạn mã cũ bằng đoạn mã mới.")
    public String updateFile(
            @ToolParam(description = "Đường dẫn tương đối tới file cần cập nhật") String path,
            @ToolParam(description = "Đoạn văn bản/mã nguồn cũ chính xác cần thay thế") String targetContent,
            @ToolParam(description = "Đoạn văn bản/mã nguồn mới thay thế vào") String replacementContent) {
        return fileSystemService.edit(path, targetContent, replacementContent);
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
