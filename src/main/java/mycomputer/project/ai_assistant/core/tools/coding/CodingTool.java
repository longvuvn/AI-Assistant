package mycomputer.project.ai_assistant.core.tools.coding;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class CodingTool {

    private final CodeGenerationService codeGenerationService;

    @Tool(description = "Tự động sinh bộ khung mã nguồn hoàn chỉnh (Scaffold) cho tính năng CRUD Spring Boot 3+ (gồm Entity, DTO Request/Response, Repository, Service và Controller) chuẩn Jakarta Persistence và Clean Architecture.")
    public String generateSpringCrudScaffold(
            @ToolParam(description = "Package gốc của dự án, ví dụ: 'com.example.student_management'") String basePackage,
            @ToolParam(description = "Tên thực thể (Entity) viết hoa chữ cái đầu, ví dụ: 'Product', 'Student', 'Order'") String entityName,
            @ToolParam(description = "Danh sách trường dữ liệu dạng 'Type name, Type name', ví dụ: 'String name, Double price, Integer stock'") String fields) {

        Map<String, String> files = codeGenerationService.generateSpringCrudScaffold(basePackage, entityName, fields);
        StringBuilder sb = new StringBuilder();
        sb.append("=== ĐÃ SINH BỘ KHUNG MÃ NGUỒN CRUD SPRING BOOT CHO '").append(entityName).append("' ===\n\n");

        for (Map.Entry<String, String> entry : files.entrySet()) {
            sb.append("File: `").append(entry.getKey()).append("`\n")
                    .append("```java\n")
                    .append(entry.getValue())
                    .append("```\n\n");
        }

        sb.append("--> BƯỚC TIẾP THEO: Hãy dùng công cụ 'createFile' để lưu các file trên vào thư mục mã nguồn tương ứng trong workspace.");
        return sb.toString();
    }

    @Tool(description = "Lấy mã nguồn mẫu chuẩn (Template/Pattern) cho Spring Boot (ví dụ: 'spring-api-response', 'spring-exception-handler', 'spring-base-entity', 'spring-security-config', 'spring-unit-test') hoặc giải thuật ('java-graph', 'java-unionfind', 'java-test').")
    public String getTemplate(
            @ToolParam(description = "Tên mẫu cần lấy, ví dụ: 'spring-api-response', 'spring-exception-handler', 'spring-unit-test', 'java-graph'") String templateKey,
            @ToolParam(description = "Package gốc dự án nếu có, ví dụ: 'com.example.demo'") String basePackage,
            @ToolParam(description = "Tên thực thể nếu có, ví dụ: 'Student', 'Product'") String entityName) {
        return codeGenerationService.getTemplate(templateKey, basePackage, entityName);
    }

    @Tool(description = "Lấy mã nguồn mẫu chuẩn cho các cấu trúc dữ liệu hoặc thuật toán nâng cao (tương thích ngược).")
    public String getAlgorithmTemplate(
            @ToolParam(description = "Ngôn ngữ: 'java' hoặc 'python'") String language,
            @ToolParam(description = "Loại mẫu: 'graph', 'unionfind', hoặc 'test'") String category) {
        return codeGenerationService.getTemplate(language + "-" + category, null, null);
    }

    @Tool(description = "Lập kế hoạch thuật toán hoặc kiến trúc module trước khi bắt đầu tạo file code.")
    public String planAlgorithm(
            @ToolParam(description = "Tên thuật toán hoặc bài toán (ví dụ: 'Dijkstra', 'Trie', 'MergeSort')") String algorithmName,
            @ToolParam(description = "Ngôn ngữ dự kiến (Java, Python, C++)") String language,
            @ToolParam(description = "Cấu trúc dữ liệu và chiến lược chính (ví dụ: PriorityQueue, Dynamic Programming, Memoization)") String strategy,
            @ToolParam(description = "Mục tiêu độ phức tạp thời gian & không gian (ví dụ: O(E log V) và O(V))") String complexityGoal) {
        return String.format("ĐÃ LẬP KẾ HOẠCH CHO %s (%s):\n- Chiến lược: %s\n- Mục tiêu Big-O: %s\n-> Hãy tiến hành tạo file mã nguồn và file test tương ứng trong workspace.",
                algorithmName, language, strategy, complexityGoal);
    }
}
