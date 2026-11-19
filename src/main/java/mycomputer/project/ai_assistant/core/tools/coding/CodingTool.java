package mycomputer.project.ai_assistant.core.tools.coding;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CodingTool {

    private final CodeGenerationService codeGenerationService;

    @Tool(description = "Lập kế hoạch thuật toán hoặc kiến trúc module trước khi bắt đầu tạo file code.")
    public String planAlgorithm(
            @ToolParam(description = "Tên thuật toán hoặc bài toán (ví dụ: 'Dijkstra', 'Trie', 'MergeSort')") String algorithmName,
            @ToolParam(description = "Ngôn ngữ dự kiến (Java, Python, C++)") String language,
            @ToolParam(description = "Cấu trúc dữ liệu và chiến lược chính (ví dụ: PriorityQueue, Dynamic Programming, Memoization)") String strategy,
            @ToolParam(description = "Mục tiêu độ phức tạp thời gian & không gian (ví dụ: O(E log V) và O(V))") String complexityGoal) {
        return String.format("ĐÃ LẬP KẾ HOẠCH CHO %s (%s):\n- Chiến lược: %s\n- Mục tiêu Big-O: %s\n-> Hãy tiến hành tạo file mã nguồn và file test tương ứng trong workspace.",
                algorithmName, language, strategy, complexityGoal);
    }

    @Tool(description = "Lấy mã nguồn mẫu chuẩn (boilerplate/pattern) cho cấu trúc dữ liệu hoặc kiểm thử (ví dụ: java-graph, java-unionfind, java-test).")
    public String getAlgorithmTemplate(
            @ToolParam(description = "Ngôn ngữ: 'java' hoặc 'python'") String language,
            @ToolParam(description = "Loại mẫu: 'graph', 'unionfind', hoặc 'test'") String category) {
        return codeGenerationService.getTemplate(language, category);
    }
}
