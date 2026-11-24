package mycomputer.project.ai_assistant.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import mycomputer.project.ai_assistant.dto.response.chat.ChatResponseDto;
import mycomputer.project.ai_assistant.service.ChatAssistantService;
import reactor.core.publisher.Flux;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatAssistantServiceImpl implements ChatAssistantService {

    @Value("${spring.ai.ollama.chat.model:qwen2.5:3b}")
    private String model;

    private final ChatClient chatClient;
    private final ChatClient memoryChatClient;
    private final ChatMemory chatMemory;
    private final JdbcTemplate jdbcTemplate;

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
                .user(message)
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

    @Override
    public void clearConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        try {
            chatMemory.clear(conversationId);
            int rows = jdbcTemplate.update("DELETE FROM spring_ai_chat_memory WHERE conversation_id = ?", conversationId);
            log.info("Đã xóa {} bản ghi lịch sử hội thoại cho conversationId: {}", rows, conversationId);
        } catch (Exception e) {
            log.warn("Lỗi khi xóa lịch sử hội thoại cho conversationId '{}': {}", conversationId, e.getMessage());
        }
    }
}
