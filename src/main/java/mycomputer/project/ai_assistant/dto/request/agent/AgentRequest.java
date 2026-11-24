package mycomputer.project.ai_assistant.dto.request.agent;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentRequest(
        @NotBlank(message = "Nội dung yêu cầu (message) không được để trống")
        @Size(max = 4000, message = "message must be at most 4000 characters")
        String message,
        String subDirectory,
        @NotBlank(message = "Đường dẫn thư mục làm việc (workspacePath) là bắt buộc. Ví dụ: D:/AI-Analytics")
        String workspacePath
) {
    public AgentRequest(String message) {
        this(message, null, null);
    }

    public AgentRequest(String message, String subDirectory) {
        this(message, subDirectory, "target/test-workspace");
    }
}
