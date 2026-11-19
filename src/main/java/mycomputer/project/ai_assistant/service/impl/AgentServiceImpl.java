package mycomputer.project.ai_assistant.service.impl;

import lombok.extern.slf4j.Slf4j;
import mycomputer.project.ai_assistant.core.tools.coding.CodingTool;
import mycomputer.project.ai_assistant.core.tools.filesystem.FileSystemService;
import mycomputer.project.ai_assistant.core.tools.filesystem.FileSystemTool;
import mycomputer.project.ai_assistant.core.tools.project.ProjectTool;
import mycomputer.project.ai_assistant.core.tools.terminal.TerminalTool;
import mycomputer.project.ai_assistant.core.tools.testing.TestExecutionService;
import mycomputer.project.ai_assistant.core.tools.testing.TestTool;
import mycomputer.project.ai_assistant.dto.request.agent.AgentRequest;
import mycomputer.project.ai_assistant.dto.response.agent.AgentResponseDto;
import mycomputer.project.ai_assistant.service.AgentService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class AgentServiceImpl implements AgentService {

    private final ChatClient codingChatClient;
    private final FileSystemService fileSystemService;
    private final TestExecutionService testExecutionService;

    public AgentServiceImpl(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            FileSystemService fileSystemService,
            FileSystemTool fileSystemTool,
            TerminalTool terminalTool,
            TestTool testTool,
            ProjectTool projectTool,
            CodingTool codingTool,
            TestExecutionService testExecutionService,
            @Value("${app.agents.coding.prompt-file:classpath:prompts/coding/01-coding.st}") Resource codingPromptResource) {

        this.fileSystemService = fileSystemService;
        this.testExecutionService = testExecutionService;

        this.codingChatClient = builder.clone()
                .defaultSystem(codingPromptResource)
                .defaultTools(fileSystemTool, terminalTool, testTool, projectTool, codingTool)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Override
    public String codingAlgorithm(String message) {
        AgentResponseDto response = executeCodingTask(new AgentRequest(message));
        return response.answer();
    }

    @Override
    public AgentResponseDto executeCodingTask(AgentRequest request) {
        String convId = (request.conversationId() != null && !request.conversationId().isBlank())
                ? request.conversationId()
                : UUID.randomUUID().toString();

        fileSystemService.clearRecentlyModifiedFiles();

        String userPrompt = request.message();
        if (request.subDirectory() != null && !request.subDirectory().isBlank()) {
            userPrompt = "[Thư mục làm việc mục tiêu trong workspace: " + request.subDirectory() + "]\n" + userPrompt;
        }

        log.info("Agent bắt đầu xử lý task cho conversationId: {}", convId);

        String answer = codingChatClient.prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, convId))
                .user(userPrompt)
                .call()
                .content();

        List<String> affectedFiles = fileSystemService.getRecentlyModifiedFiles();
        log.info("Agent hoàn thành task cho {}. Số file đã tạo/sửa: {}", convId, affectedFiles.size());

        return AgentResponseDto.of(answer, convId, affectedFiles);
    }

    @Override
    public AgentResponseDto runTests(String projectDir, String testClassName) {
        TestExecutionService.TestResult result = testExecutionService.runMavenTests(projectDir, testClassName);
        String convId = UUID.randomUUID().toString();
        return AgentResponseDto.of(result.toFormattedString(), convId, List.of());
    }

    @Override
    public AgentResponseDto fixAndRetest(String taskDescription, String failureDetails) {
        String prompt = String.format("""
                Yêu cầu ban đầu: %s

                Chi tiết lỗi gặp phải khi kiểm thử / biên dịch:
                %s

                HƯỚNG DẪN THỰC HIỆN:
                1. Đọc kỹ chi tiết lỗi bên trên.
                2. Dùng công cụ readFile hoặc listFiles để kiểm tra tệp tin liên quan trong workspace.
                3. Sửa lại mã nguồn (dùng updateFile hoặc createFile).
                4. Chạy lại kiểm thử tương ứng (runJavaAssertionTest, runMavenTest, hoặc runPythonTest).
                5. Tiếp tục tối ưu và sửa cho đến khi tất cả các kiểm thử đều PASSED.
                6. Báo cáo nguyên nhân lỗi và cách đã giải quyết.
                """, taskDescription, failureDetails);

        return executeCodingTask(new AgentRequest(prompt));
    }
}
