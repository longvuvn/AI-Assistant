package mycomputer.project.ai_assistant.config;

import lombok.RequiredArgsConstructor;
import mycomputer.project.ai_assistant.core.tools.coding.CodingTool;
import mycomputer.project.ai_assistant.core.tools.filesystem.FileSystemTool;
import mycomputer.project.ai_assistant.core.tools.project.ProjectTool;
import mycomputer.project.ai_assistant.core.tools.terminal.TerminalTool;
import mycomputer.project.ai_assistant.core.tools.testing.TestTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

@Configuration
@RequiredArgsConstructor
public class MultiAgentConfig {

    @Value("classpath:prompts/agents/01-ba.st")
    private Resource baPrompt;

    @Value("classpath:prompts/agents/02-architect.st")
    private Resource architectPrompt;

    @Value("classpath:prompts/agents/03-api-designer.st")
    private Resource apiDesignerPrompt;

    @Value("classpath:prompts/agents/04-developer.st")
    private Resource developerPrompt;

    @Value("classpath:prompts/agents/05-tester.st")
    private Resource testerPrompt;

    @Value("classpath:prompts/agents/06-security.st")
    private Resource securityPrompt;

    @Value("classpath:prompts/agents/07-devops.st")
    private Resource devopsPrompt;

    @Bean
    public ChatClient baAgentClient(OllamaChatModel codingChatModel) {
        return ChatClient.builder(codingChatModel)
                .defaultSystem(baPrompt)
                .build();
    }

    @Bean
    public ChatClient architectAgentClient(OllamaChatModel codingChatModel, ProjectTool projectTool) {
        return ChatClient.builder(codingChatModel)
                .defaultSystem(architectPrompt)
                .defaultTools(projectTool)
                .build();
    }

    @Bean
    public ChatClient apiDesignerAgentClient(OllamaChatModel codingChatModel, CodingTool codingTool) {
        return ChatClient.builder(codingChatModel)
                .defaultSystem(apiDesignerPrompt)
                .defaultTools(codingTool)
                .build();
    }

    @Bean
    public ChatClient developerAgentClient(OllamaChatModel codingChatModel, FileSystemTool fsTool, CodingTool codingTool, ProjectTool projTool) {
        return ChatClient.builder(codingChatModel)
                .defaultSystem(developerPrompt)
                .defaultTools(fsTool, codingTool, projTool)
                .build();
    }

    @Bean
    public ChatClient testerAgentClient(OllamaChatModel codingChatModel, TestTool testTool, FileSystemTool fsTool) {
        return ChatClient.builder(codingChatModel)
                .defaultSystem(testerPrompt)
                .defaultTools(testTool, fsTool)
                .build();
    }

    @Bean
    public ChatClient securityAgentClient(OllamaChatModel codingChatModel, FileSystemTool fsTool) {
        return ChatClient.builder(codingChatModel)
                .defaultSystem(securityPrompt)
                .defaultTools(fsTool)
                .build();
    }

    @Bean
    public ChatClient devopsAgentClient(OllamaChatModel codingChatModel, FileSystemTool fsTool, TerminalTool terminalTool) {
        return ChatClient.builder(codingChatModel)
                .defaultSystem(devopsPrompt)
                .defaultTools(fsTool, terminalTool)
                .build();
    }
}
