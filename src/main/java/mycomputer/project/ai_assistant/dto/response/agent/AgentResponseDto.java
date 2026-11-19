package mycomputer.project.ai_assistant.dto.response.agent;

import java.time.Instant;
import java.util.List;

public record AgentResponseDto(
        String answer,
        String conversationId,
        List<String> affectedFiles,
        Instant createdAt
) {
    public static AgentResponseDto of(String answer, String conversationId, List<String> affectedFiles) {
        return new AgentResponseDto(answer, conversationId, affectedFiles, Instant.now());
    }
}
