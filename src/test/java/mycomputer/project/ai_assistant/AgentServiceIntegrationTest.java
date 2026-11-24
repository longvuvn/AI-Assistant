package mycomputer.project.ai_assistant;

import mycomputer.project.ai_assistant.dto.request.agent.AgentRequest;
import mycomputer.project.ai_assistant.dto.response.agent.AgentResponseDto;
import mycomputer.project.ai_assistant.service.AgentService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AgentServiceIntegrationTest {

    @Autowired
    private AgentService agentService;

    @Test
    void testAgentContextWiring() {
        assertNotNull(agentService, "AgentService should be properly injected and configured with ChatClient and tools");
    }

    // Test gọi trực tiếp agent (có thể chạy khi cần test e2e với Ollama)
    @Test
    @Disabled("Chạy thủ công để kiểm thử trực tiếp quá trình suy luận của Ollama")
    void testLiveAgentExecution() {
        AgentRequest request = new AgentRequest(
                "Viết class Java QuickSort và hàm main kiểm thử sắp xếp mảng số nguyên. Lưu vào workspace và chạy test kiểm tra.",
                "quicksort-demo"
        );
        String conversationId = UUID.randomUUID().toString();
        AgentResponseDto response = agentService.executeCodingTask(conversationId, request);
        assertNotNull(response);
        assertNotNull(response.answer());
        System.out.println("Agent response: " + response.answer());
        System.out.println("Affected files: " + response.affectedFiles());
    }
}
