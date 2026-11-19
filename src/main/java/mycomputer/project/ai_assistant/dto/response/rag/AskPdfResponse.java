package mycomputer.project.ai_assistant.dto.response.rag;

import java.util.List;

public record AskPdfResponse(
        String answer,
        List<PdfSourceDto> sources
) {
}
