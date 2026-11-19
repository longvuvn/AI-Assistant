package mycomputer.project.ai_assistant.dto.request.agent;

import jakarta.validation.constraints.NotBlank;

public record AgentRequest(
        @NotBlank(message = "Nội dung yêu cầu (message) không được để trống")
        String message,
        String conversationId,
        String subDirectory
) {
    public AgentRequest(String message) {
        this(message, null, null);
    }
}
