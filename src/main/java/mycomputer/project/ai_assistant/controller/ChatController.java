package mycomputer.project.ai_assistant.controller;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
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

    @PostMapping("/explain")
    public ResponseEntity<ChatResponseDto> explain(@Valid @RequestBody ExplainRequest request) {
        return ResponseEntity.status(HttpStatus.OK).body(chatAssistantService.explainForBeginner(
                request.topic(),
                request.safeAudience()));
    }
}
