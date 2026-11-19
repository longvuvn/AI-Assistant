package mycomputer.project.ai_assistant.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mycomputer.project.ai_assistant.dto.request.rag.AskPdfRequest;
import mycomputer.project.ai_assistant.dto.request.rag.PdfSearchRequest;
import mycomputer.project.ai_assistant.dto.response.rag.AskPdfResponse;
import mycomputer.project.ai_assistant.dto.response.rag.PdfSourceDto;
import mycomputer.project.ai_assistant.dto.response.rag.UploadPdfResponse;
import mycomputer.project.ai_assistant.service.RagService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rag")
@RequiredArgsConstructor
public class RagController {

    private final RagService ragService;

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(Map.of("status", "UP", "service", "pdf-knowledge-assistant"));
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadPdfResponse> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ragService.uploadAndIngest(file));
    }

    @PostMapping("/search")
    public ResponseEntity<List<PdfSourceDto>> search(@Valid @RequestBody PdfSearchRequest request) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ragService.search(request));
    }

    @PostMapping("/ask")
    public ResponseEntity<AskPdfResponse> ask(@Valid @RequestBody AskPdfRequest request) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ragService.ask(request));
    }

    @PostMapping("/ask-manual")
    public ResponseEntity<AskPdfResponse> askManual(@Valid @RequestBody AskPdfRequest request) {
        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ragService.askManual(request));
    }
}