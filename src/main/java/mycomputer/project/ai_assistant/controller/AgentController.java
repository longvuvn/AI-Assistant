package mycomputer.project.ai_assistant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mycomputer.project.ai_assistant.dto.request.agent.AgentRequest;
import mycomputer.project.ai_assistant.dto.response.agent.AgentResponseDto;
import mycomputer.project.ai_assistant.service.AgentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/agent")
@RequiredArgsConstructor
@Tag(name = "3. AI Coding Agent", description = "Các tác vụ lập trình ReAct loop, tự động phân tích dự án, sinh mã nguồn và kiểm thử")
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
        return ResponseEntity.ok(agentService.createCodingTask(request));
    }

    /**
     * Endpoint chạy tiếp tục nhiệm vụ trên conversationId (đồng bộ)
     */
    @PostMapping("/task/{conversationId}")
    public ResponseEntity<AgentResponseDto> continueTask(@PathVariable String conversationId,
                                                         @Valid @RequestBody AgentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(agentService.executeCodingTask(conversationId, request));
    }

    /**
     * Endpoint SSE Streaming (POST): Nhận AgentRequest DTO và stream real-time các sự kiện tiến trình của Agent
     */
    @PostMapping(value = "/task/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTask(@Valid @RequestBody AgentRequest request) {
        return agentService.streamCodingTask(request);
    }

    /**
     * Endpoint SSE Streaming (POST): Stream real-time tiếp tục nhiệm vụ trên conversationId
     */
    @PostMapping(value = "/task/{conversationId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamContinueTask(@PathVariable String conversationId,
                                         @Valid @RequestBody AgentRequest request) {
        return agentService.streamCodingTask(conversationId, request);
    }

    /**
     * Endpoint SSE Streaming (GET): Hỗ trợ kết nối trực tiếp từ trình duyệt (EventSource chuẩn HTML5)
     */
    @GetMapping(value = "/task/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamTaskGet(
            @RequestParam String workspacePath,
            @RequestParam String message,
            @RequestParam(required = false) String subDirectory,
            @RequestParam(required = false) String conversationId) {

        AgentRequest request = new AgentRequest(message, subDirectory, workspacePath);
        if (conversationId != null && !conversationId.isBlank()) {
            return agentService.streamCodingTask(conversationId, request);
        }
        return agentService.streamCodingTask(request);
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
