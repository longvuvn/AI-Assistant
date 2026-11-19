package mycomputer.project.ai_assistant.dto.request.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ExplainRequest(
        @NotBlank(message = "topic is required") @Size(max = 500, message = "topic must be less than 500 characters") String topic,

        String audience) {
    public String safeAudience() {
        if (audience == null || audience.isBlank()) {
            return "fresher Java developer";
        }
        return audience;
    }
}