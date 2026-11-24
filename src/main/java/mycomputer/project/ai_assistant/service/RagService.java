package mycomputer.project.ai_assistant.service;

import mycomputer.project.ai_assistant.dto.request.rag.AskPdfRequest;
import mycomputer.project.ai_assistant.dto.request.rag.PdfSearchRequest;
import mycomputer.project.ai_assistant.dto.response.rag.AskPdfResponse;
import mycomputer.project.ai_assistant.dto.response.rag.PdfSourceDto;
import mycomputer.project.ai_assistant.dto.response.rag.UploadPdfResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RagService {
    UploadPdfResponse uploadAndIngest(MultipartFile file);
    List<PdfSourceDto> search(PdfSearchRequest request);
    AskPdfResponse ask(AskPdfRequest request);
    AskPdfResponse askManual(AskPdfRequest request);
    void deleteDocument(String documentId);
}
