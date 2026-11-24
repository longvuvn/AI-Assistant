package mycomputer.project.ai_assistant.dto.response.agent;

import java.time.Instant;

public record AgentTaskEvent(
        String conversationId,
        AgentEventType type,
        String message,
        Object payload,
        Instant timestamp
) {
    public static AgentTaskEvent of(String conversationId, AgentEventType type, String message, Object payload) {
        return new AgentTaskEvent(conversationId, type, message, payload, Instant.now());
    }

    public static AgentTaskEvent of(String conversationId, AgentEventType type, String message) {
        return new AgentTaskEvent(conversationId, type, message, null, Instant.now());
    }
}
