package mycomputer.project.ai_assistant.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.Resource;

@Configuration
public class ChatClientConfig {

    @Value("${app.base-prompt-file:classpath:prompts/base/01-role.st}")
    private Resource basePromptResource;

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
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
}
