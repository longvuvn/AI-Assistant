package mycomputer.project.ai_assistant.dto.request.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ConversationRequest(
        @NotBlank(message = "conversationId is required") @Size(max = 100, message = "conversationId must be less than 100 characters") String conversationId,

        @NotBlank(message = "message is required") @Size(max = 4000, message = "message must be less than 4000 characters") String message) {
}
