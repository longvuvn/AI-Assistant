package mycomputer.project.ai_assistant;

import mycomputer.project.ai_assistant.dto.request.agent.AgentRequest;
import mycomputer.project.ai_assistant.dto.response.agent.AgentEventType;
import mycomputer.project.ai_assistant.dto.response.agent.AgentTaskEvent;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AgentSseStreamTest {

    @Test
    void testAgentTaskEventCreation() {
        String convId = "test-conv-123";
        AgentTaskEvent event = AgentTaskEvent.of(
                convId,
                AgentEventType.TOOL_CALL,
                "Đang tạo file Student.java",
                Map.of("file", "Student.java")
        );

        assertNotNull(event);
        assertEquals(convId, event.conversationId());
        assertEquals(AgentEventType.TOOL_CALL, event.type());
        assertEquals("Đang tạo file Student.java", event.message());
        assertNotNull(event.timestamp());
        assertNotNull(event.payload());
    }

    @Test
    void testAgentEventTypes() {
        assertEquals(8, AgentEventType.values().length);
        assertNotNull(AgentEventType.valueOf("START"));
        assertNotNull(AgentEventType.valueOf("ANALYZING"));
        assertNotNull(AgentEventType.valueOf("THINKING"));
        assertNotNull(AgentEventType.valueOf("TOOL_CALL"));
        assertNotNull(AgentEventType.valueOf("TOOL_RESULT"));
        assertNotNull(AgentEventType.valueOf("FILE_WRITTEN"));
        assertNotNull(AgentEventType.valueOf("COMPLETE"));
        assertNotNull(AgentEventType.valueOf("ERROR"));
    }
}
