package mycomputer.project.ai_assistant.service;

import mycomputer.project.ai_assistant.dto.request.agent.AgentRequest;
import mycomputer.project.ai_assistant.dto.response.agent.AgentResponseDto;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface AgentService {

    /**
     * Tương thích ngược với endpoint cũ: Nhận yêu cầu và sinh mã/thuật toán kèm kiểm thử
     */
    String codingAlgorithm(String message);

    /**
     * Thực thi nhiệm vụ lập trình và kiểm thử tự động toàn diện của AI Agent (đồng bộ)
     */
    AgentResponseDto createCodingTask(AgentRequest request);

    /**
     * Thực thi tiếp tục nhiệm vụ lập trình và kiểm thử tự động toàn diện của AI Agent trên conversationId (đồng bộ)
     */
    AgentResponseDto executeCodingTask(String conversationId, AgentRequest request);

    /**
     * Stream real-time các sự kiện (Server-Sent Events) trong quá trình thực thi nhiệm vụ của Agent
     */
    SseEmitter streamCodingTask(AgentRequest request);

    /**
     * Stream real-time các sự kiện (Server-Sent Events) tiếp tục nhiệm vụ trên conversationId
     */
    SseEmitter streamCodingTask(String conversationId, AgentRequest request);

    /**
     * Chạy trực tiếp bộ kiểm thử trong workspace
     */
    AgentResponseDto runTests(String projectDir, String testClassName);

    /**
     * Yêu cầu AI Agent phân tích lỗi test/biên dịch, tự sửa mã và chạy lại test
     */
    AgentResponseDto fixAndRetest(String taskDescription, String failureDetails);
}
