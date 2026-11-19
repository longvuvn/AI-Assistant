package mycomputer.project.ai_assistant.dto.response.chat;

import java.time.Instant;

public record ChatResponseDto(
        String answer,
        String model,
        Instant createdAt) {
    public static ChatResponseDto of(String answer, String model) {
        return new ChatResponseDto(answer, model, Instant.now());
    }
}
