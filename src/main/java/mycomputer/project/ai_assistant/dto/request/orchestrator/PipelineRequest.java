package mycomputer.project.ai_assistant.dto.request.orchestrator;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PipelineRequest(
        @NotBlank(message = "Đề bài hoặc yêu cầu (message) không được để trống")
        @Size(max = 5000, message = "Đề bài tối đa 5000 ký tự")
        String message,

        @NotBlank(message = "Đường dẫn workspace là bắt buộc")
        String workspacePath,

        String subDirectory
) {
    public PipelineRequest(String message, String workspacePath) {
        this(message, workspacePath, null);
    }
}
