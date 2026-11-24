package mycomputer.project.ai_assistant.service;

import mycomputer.project.ai_assistant.dto.response.chat.ChatResponseDto;
import reactor.core.publisher.Flux;

public interface ChatAssistantService {
    ChatResponseDto ask(String message);
    Flux<String> stream(String message);
    ChatResponseDto askWithMemory(String conversationId, String message);
    ChatResponseDto explainForBeginner(String topic, String audience);
    void clearConversation(String conversationId);
}
