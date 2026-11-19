package mycomputer.project.ai_assistant.dto.response.rag;

public record PdfSourceDto(
        String content,
        String fileName,
        String documentId,
        String page,
        Double score
) {
}
