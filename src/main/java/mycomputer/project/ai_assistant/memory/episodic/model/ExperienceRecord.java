package mycomputer.project.ai_assistant.memory.episodic.model;

import java.time.Instant;
import java.util.List;

public record ExperienceRecord(
        String id,
        String errorType,          // e.g., "MAVEN_COMPILE_ERROR", "UNIT_TEST_FAILURE", "RUNTIME_EXCEPTION"
        String errorSummary,        // Tiêu đề ngắn gọn của lỗi
        String errorDetails,        // Stacktrace hoặc log chi tiết
        String solutionSummary,     // Tóm tắt giải pháp đã khắc phục
        String fixedCodeSnippet,    // Đoạn mã nguồn mẫu đã sửa thành công
        String affectedFile,        // Tệp tin bị ảnh hưởng (ví dụ: src/main/java/.../Student.java)
        String taskContext,         // Yêu cầu bài toán ban đầu
        List<String> tags,          // ["maven", "jakarta", "spring-boot-3"]
        Instant createdAt,
        Double similarityScore
) {
    public ExperienceRecord(String id, String errorType, String errorSummary, String errorDetails,
                            String solutionSummary, String fixedCodeSnippet, String affectedFile, String taskContext, List<String> tags) {
        this(id, errorType, errorSummary, errorDetails, solutionSummary, fixedCodeSnippet, affectedFile, taskContext, tags, Instant.now(), null);
    }
}
