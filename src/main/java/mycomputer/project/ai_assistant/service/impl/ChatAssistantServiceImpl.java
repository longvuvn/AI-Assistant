package mycomputer.project.ai_assistant.service.impl;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mycomputer.project.ai_assistant.dto.response.chat.ChatResponseDto;
import mycomputer.project.ai_assistant.service.ChatAssistantService;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
public class ChatAssistantServiceImpl implements ChatAssistantService {

    @Value("${spring.ai.ollama.chat.model}")
    private String model;

    private final ChatClient chatClient;
    private final ChatClient memoryChatClient;

    @Override
    public ChatResponseDto ask(String message) {
        String answer = chatClient
                .prompt()
                .user(message)
                .call()
                .content();
        return ChatResponseDto.of(answer, model);
    }

    @Override
    public Flux<String> stream(String message) {
        return chatClient
                .prompt()
                .user(message)
                .stream()
                .content();
    }

    @Override
    public ChatResponseDto askWithMemory(String conversationId, String message) {
        String answer = memoryChatClient
                .prompt()
                .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
        return ChatResponseDto.of(answer, model);
    }

    @Override
    public ChatResponseDto explainForBeginner(String topic, String audience) {
        String answer = chatClient
                .prompt()
                .user(userSpec -> userSpec
                        .text("""
                                Explain the topic below for a {audience}.

                                Topic:
                                {topic}

                                Response format:
                                1. Simple definition
                                2. Why it matters
                                3. Step-by-step explanation
                                4. Small Java or Spring Boot example if useful
                                5. Common mistake to avoid
                                """)
                        .param("audience", audience)
                        .param("topic", topic))
                .call()
                .content();

        return ChatResponseDto.of(answer, model);
    }

}
