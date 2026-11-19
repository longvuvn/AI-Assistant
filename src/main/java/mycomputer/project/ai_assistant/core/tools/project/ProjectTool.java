package mycomputer.project.ai_assistant.core.tools.project;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProjectTool {

    private final ProjectAnalysisService projectAnalysisService;

    @Tool(description = "Hiển thị cây cấu trúc thư mục của dự án trong workspace.")
    public String getProjectStructure(
            @ToolParam(description = "Thư mục tương đối cần xem cấu trúc (để trống hoặc '.' để xem từ thư mục gốc)") String subDir) {
        return projectAnalysisService.getProjectTree(subDir, 4);
    }

    @Tool(description = "Tự động nhận diện loại công nghệ hoặc dự án trong workspace (ví dụ: Maven, Gradle, Python, Node.js).")
    public String detectProjectType(
            @ToolParam(description = "Thư mục tương đối của dự án cần nhận diện") String subDir) {
        return projectAnalysisService.detectProjectType(subDir);
    }

    @Tool(description = "Tìm kiếm từ khóa, tên hàm, class hoặc biến trong các tệp tin mã nguồn của workspace.")
    public String searchCode(
            @ToolParam(description = "Từ khóa cần tìm") String query,
            @ToolParam(description = "Đuôi file cần lọc, ví dụ: '.java' hoặc '.py', để trống để tìm tất cả") String fileExtension) {
        return projectAnalysisService.searchInFiles(query, fileExtension);
    }
}
