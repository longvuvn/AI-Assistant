package mycomputer.project.ai_assistant.dto.request.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequestDto(
                @NotBlank(message = "message is required") @Size(max = 400, message = "message must be less than 4000 characters") String message) {
}
