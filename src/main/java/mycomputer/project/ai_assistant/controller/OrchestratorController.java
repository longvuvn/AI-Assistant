package mycomputer.project.ai_assistant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mycomputer.project.ai_assistant.dto.request.orchestrator.PipelineRequest;
import mycomputer.project.ai_assistant.dto.response.orchestrator.PipelineResponse;
import mycomputer.project.ai_assistant.orchestrator.service.MultiAgentOrchestratorService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/orchestrator")
@RequiredArgsConstructor
@Tag(name = "1. Multi-Agent SDLC Orchestrator", description = "Điều phối quy trình phát triển phần mềm qua 7 AI Agent tuần tự (BA -> Architect -> API Designer -> Developer -> Tester -> Security -> DevOps)")
public class OrchestratorController {

    private final MultiAgentOrchestratorService orchestratorService;

    /**
     * Thực thi toàn bộ chu trình phát triển phần mềm (SDLC) qua 7 AI Agent tuần tự (đồng bộ).
     */
    @Operation(summary = "Thực thi toàn bộ chu trình SDLC 7 Agent (Đồng bộ)")
    @PostMapping("/pipeline")
    public ResponseEntity<PipelineResponse> executePipeline(@Valid @RequestBody PipelineRequest request) {
        PipelineResponse response = orchestratorService.executePipeline(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Stream real-time các sự kiện tiến trình qua Server-Sent Events (SSE) theo phương thức POST.
     */
    @Operation(summary = "Stream real-time tiến trình SDLC qua SSE (POST)")
    @PostMapping(value = "/pipeline/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPipelinePost(@Valid @RequestBody PipelineRequest request) {
        return orchestratorService.streamPipeline(request);
    }

    /**
     * Stream real-time qua phương thức GET hỗ trợ kết nối trực tiếp từ EventSource HTML5 trong trình duyệt hoặc frontend.
     */
    @Operation(summary = "Stream real-time tiến trình SDLC qua SSE (GET cho EventSource)")
    @GetMapping(value = "/pipeline/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamPipelineGet(
            @RequestParam String workspacePath,
            @RequestParam String message,
            @RequestParam(required = false) String subDirectory) {

        PipelineRequest request = new PipelineRequest(message, workspacePath, subDirectory);
        return orchestratorService.streamPipeline(request);
    }
}
