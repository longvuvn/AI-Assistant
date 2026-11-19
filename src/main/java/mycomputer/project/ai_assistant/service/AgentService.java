package mycomputer.project.ai_assistant.service;

import mycomputer.project.ai_assistant.dto.request.agent.AgentRequest;
import mycomputer.project.ai_assistant.dto.response.agent.AgentResponseDto;

public interface AgentService {

    /**
     * Tương thích ngược với endpoint cũ: Nhận yêu cầu và sinh mã/thuật toán kèm kiểm thử
     */
    String codingAlgorithm(String message);

    /**
     * Thực thi nhiệm vụ lập trình và kiểm thử tự động toàn diện của AI Agent
     */
    AgentResponseDto executeCodingTask(AgentRequest request);

    /**
     * Chạy trực tiếp bộ kiểm thử trong workspace
     */
    AgentResponseDto runTests(String projectDir, String testClassName);

    /**
     * Yêu cầu AI Agent phân tích lỗi test/biên dịch, tự sửa mã và chạy lại test
     */
    AgentResponseDto fixAndRetest(String taskDescription, String failureDetails);
}
