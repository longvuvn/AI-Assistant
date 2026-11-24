package mycomputer.project.ai_assistant.config;

import lombok.RequiredArgsConstructor;
import mycomputer.project.ai_assistant.core.tools.coding.CodingTool;
import mycomputer.project.ai_assistant.core.tools.filesystem.FileSystemTool;
import mycomputer.project.ai_assistant.core.tools.project.ProjectTool;
import mycomputer.project.ai_assistant.core.tools.terminal.TerminalTool;
import mycomputer.project.ai_assistant.core.tools.testing.TestTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;

@Configuration
@RequiredArgsConstructor
public class ChatClientConfig {

    @Value("${app.base-prompt-file:classpath}")
    private Resource basePromptResource;

    @Value("${app.agents.coding.prompt-file:classpath}")
    private Resource codingPromptResource;

    @Value("${app.agents.coding.model}")
    private String model;

    @Value("${app.agents.coding.temperature}")
    private double codingTemperature;

    @Value("${app.agents.coding.num-ctx}")
    private int codingNumCtx;

    @Value("${app.agents.coding.num-predict}")
    private int codingNumPredict;

    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(20)
                .build();
    }

    @Bean
    @Primary
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.clone()
                .defaultSystem(basePromptResource)
                .build();
    }

    @Bean
    ChatClient memoryChatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder.clone()
                .defaultSystem(basePromptResource)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean
    public OllamaChatModel codingChatModel(OllamaApi ollamaApi) {
        return OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(OllamaChatOptions.builder()
                        .model(model) // "qwen2.5-coder:7b"
                        .temperature(codingTemperature)
                        .numCtx(codingNumCtx)
                        .numPredict(codingNumPredict)
                        .build())
                .build();
    }

    @Bean
    ChatClient codingChatClient(OllamaChatModel codingChatModel, ChatMemory chatMemory, FileSystemTool fileSystemTool, TerminalTool terminalTool,
                                TestTool testTool, CodingTool codingTool, ProjectTool projectTool) {
        return ChatClient.builder(codingChatModel)
                .defaultSystem(codingPromptResource)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(fileSystemTool, terminalTool, testTool, codingTool, projectTool)
                .build();
    }
}
