package mycomputer.project.ai_assistant.orchestrator.service;

import mycomputer.project.ai_assistant.dto.request.orchestrator.PipelineRequest;
import mycomputer.project.ai_assistant.dto.response.orchestrator.PipelineResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface MultiAgentOrchestratorService {

    /**
     * Thực thi toàn bộ chu trình phát triển phần mềm (SDLC) qua 7 AI Agent tuần tự (đồng bộ).
     *
     * @param request Yêu cầu bài toán, workspacePath và subDirectory (nếu có)
     * @return PipelineResponse chứa kết quả bàn giao của 7 Agent và danh sách file đã tạo
     */
    PipelineResponse executePipeline(PipelineRequest request);

    /**
     * Stream real-time toàn bộ tiến trình 7 AI Agent qua Server-Sent Events (SSE).
     *
     * @param request Yêu cầu bài toán, workspacePath và subDirectory (nếu có)
     * @return SseEmitter phát các sự kiện PipelineStageEvent theo thời gian thực
     */
    SseEmitter streamPipeline(PipelineRequest request);
}
