package mycomputer.project.ai_assistant.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mycomputer.project.ai_assistant.dto.request.agent.AgentRequest;
import mycomputer.project.ai_assistant.dto.response.agent.AgentResponseDto;
import mycomputer.project.ai_assistant.service.AgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    /**
     * Endpoint cũ: Nhận message thô dạng String và trả về kết quả
     */
    @PostMapping("/coding")
    public ResponseEntity<String> askAgent(@RequestBody String message) {
        return ResponseEntity.ok(agentService.codingAlgorithm(message));
    }

    /**
     * Endpoint chính: Nhận AgentRequest DTO (kèm conversationId, subDirectory nếu có)
     * Trả về AgentResponseDto gồm câu trả lời, conversationId và danh sách file đã tạo/sửa
     */
    @PostMapping("/task")
    public ResponseEntity<AgentResponseDto> executeTask(@Valid @RequestBody AgentRequest request) {
        return ResponseEntity.ok(agentService.executeCodingTask(request));
    }

    /**
     * Endpoint chạy test: Chạy unit test trực tiếp trong workspace
     */
    @PostMapping("/test")
    public ResponseEntity<AgentResponseDto> runTests(
            @RequestParam(required = false, defaultValue = "") String projectDir,
            @RequestParam(required = false, defaultValue = "") String testClassName) {
        return ResponseEntity.ok(agentService.runTests(projectDir, testClassName));
    }

    /**
     * Endpoint tự sửa lỗi: Đưa lỗi test hoặc compiler cho Agent tự phân tích, sửa code và test lại
     */
    @PostMapping("/fix")
    public ResponseEntity<AgentResponseDto> fixAndRetest(
            @RequestParam String taskDescription,
            @RequestBody String failureDetails) {
        return ResponseEntity.ok(agentService.fixAndRetest(taskDescription, failureDetails));
    }
}
