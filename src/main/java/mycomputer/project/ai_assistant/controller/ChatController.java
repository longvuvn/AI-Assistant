package mycomputer.project.ai_assistant.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mycomputer.project.ai_assistant.dto.request.chat.ChatRequestDto;
import mycomputer.project.ai_assistant.dto.request.chat.ConversationRequest;
import mycomputer.project.ai_assistant.dto.request.chat.ExplainRequest;
import mycomputer.project.ai_assistant.dto.response.chat.ChatResponseDto;
import mycomputer.project.ai_assistant.service.ChatAssistantService;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "5. Chat Assistant", description = "Hỏi đáp và giải thích thuật toán có ghi nhớ hội thoại qua JDBC PostgreSQL")
public class ChatController {

    private final ChatAssistantService chatAssistantService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok().body(Map.of("status", "UP", "service", "spring-ai-chat-assistant"));
    }

    @PostMapping("/ask")
    public ResponseEntity<ChatResponseDto> ask(@Valid @RequestBody ChatRequestDto request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(chatAssistantService.ask(request.message()));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<Flux<String>> stream(@RequestParam String message) {
        return ResponseEntity.status(HttpStatus.OK).body(chatAssistantService.stream(message));
    }

    @PostMapping("/conversation")
    public ResponseEntity<ChatResponseDto> conversation(@Valid @RequestBody ConversationRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(chatAssistantService.askWithMemory(
                request.conversationId(),
                request.message()));
    }

    @DeleteMapping("/conversation/{conversationId}")
    public ResponseEntity<Map<String, Object>> clearConversation(@PathVariable String conversationId) {
        chatAssistantService.clearConversation(conversationId);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Đã xóa toàn bộ lịch sử hội thoại cho conversationId: " + conversationId,
                "conversationId", conversationId
        ));
    }

    @PostMapping("/explain")
    public ResponseEntity<ChatResponseDto> explain(@Valid @RequestBody ExplainRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(chatAssistantService.explainForBeginner(
                request.topic(),
                request.safeAudience()));
    }
}
