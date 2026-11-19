package mycomputer.project.ai_assistant.dto.response.rag;

public record UploadPdfResponse(
        String fileName,
        String documentId,
        int pagesRead,
        int chunksStored
) {
}
