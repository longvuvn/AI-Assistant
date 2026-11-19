package mycomputer.project.ai_assistant.dto.request.rag;

import jakarta.validation.constraints.NotBlank;

public record PdfSearchRequest(
        @NotBlank(message = "query is required")
        String query,

        String documentId,

        Integer topK,

        Double similarityThreshold
) {
    public int safeTopK() {
        return topK == null ? 5 : topK;
    }

    public double safeSimilarityThreshold() {
        return similarityThreshold == null ? 0.70 : similarityThreshold;
    }
}
