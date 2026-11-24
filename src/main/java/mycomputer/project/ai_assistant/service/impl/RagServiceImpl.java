package mycomputer.project.ai_assistant.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mycomputer.project.ai_assistant.dto.request.rag.AskPdfRequest;
import mycomputer.project.ai_assistant.dto.request.rag.PdfSearchRequest;
import mycomputer.project.ai_assistant.dto.response.rag.AskPdfResponse;
import mycomputer.project.ai_assistant.dto.response.rag.PdfSourceDto;
import mycomputer.project.ai_assistant.dto.response.rag.UploadPdfResponse;
import mycomputer.project.ai_assistant.service.RagService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.ai.document.Document;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RagServiceImpl implements RagService {

    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    private final JdbcTemplate jdbcTemplate;

    @Override
    public UploadPdfResponse uploadAndIngest(MultipartFile file) {
        validatePdf(file);

        String documentId = UUID.randomUUID().toString();
        String originalFileName = cleanFileName(file.getOriginalFilename());
        Path tempFile = null;

        try {
            tempFile = Files.createTempFile("spring-ai-pdf-", ".pdf");
            file.transferTo(tempFile);

            List<Document> pages = readPdfPages(tempFile, originalFileName, documentId);
            List<Document> chunks = splitPagesIntoChunks(pages);

            vectorStore.add(chunks);

            return new UploadPdfResponse(
                    originalFileName,
                    documentId,
                    pages.size(),
                    chunks.size()
            );
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to process PDF file", ex);
        }
        finally {
            deleteTempFile(tempFile);
        }
    }

    @Override
    public List<PdfSourceDto> search(PdfSearchRequest request) {
        SearchRequest searchRequest = buildSearchRequest(
                request.query(),
                request.documentId(),
                request.safeTopK(),
                request.safeSimilarityThreshold()
        );

        return vectorStore.similaritySearch(searchRequest)
                .stream()
                .map(this::toSource)
                .toList();
    }

    @Override
    public AskPdfResponse ask(AskPdfRequest request) {
        SearchRequest searchRequest = buildSearchRequest(
                request.question(),
                request.documentId(),
                request.safeTopK(),
                request.safeSimilarityThreshold()
        );

        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(vectorStore)
                .searchRequest(searchRequest)
                .build();

        String answer = chatClient
                .prompt()
                .advisors(advisor)
                .user(request.question())
                .call()
                .content();

        List<PdfSourceDto> sources = vectorStore.similaritySearch(searchRequest)
                .stream()
                .map(this::toSource)
                .toList();

        return new AskPdfResponse(answer, sources);
    }

    @Override
    public AskPdfResponse askManual(AskPdfRequest request) {
        SearchRequest searchRequest = buildSearchRequest(
                request.question(),
                request.documentId(),
                request.safeTopK(),
                request.safeSimilarityThreshold()
        );

        List<Document> matchedDocuments = vectorStore.similaritySearch(searchRequest);

        String context = matchedDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        String prompt = """
                Use the following context to answer the question.
                If you do not know the answer, say that you do not know.

                Context:
                """ + context + """

                Question:
                """ + request.question() + """

                Answer:
                """;

        String answer = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content();

        List<PdfSourceDto> sources = matchedDocuments.stream()
                .map(this::toSource)
                .toList();

        return new AskPdfResponse(answer, sources);
    }

    @Override
    public void deleteDocument(String documentId) {
        if (documentId == null || documentId.isBlank()) {
            throw new IllegalArgumentException("documentId không được để trống");
        }
        try {
            int rows = jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'documentId' = ?", documentId);
            log.info("Đã xóa {} vector chunks cho documentId '{}'", rows, documentId);
        } catch (Exception ex) {
            log.error("Lỗi khi xóa vector chunks cho documentId '{}': {}", documentId, ex.getMessage(), ex);
            throw new IllegalStateException("Không thể xóa tài liệu: " + ex.getMessage(), ex);
        }
    }

    private List<Document> readPdfPages(Path pdfPath, String fileName, String documentId) {
        PagePdfDocumentReader reader = new PagePdfDocumentReader(
                pdfPath.toUri().toString(),
                PdfDocumentReaderConfig.builder()
                        .withPageTopMargin(0)
                        .withPageExtractedTextFormatter(
                                ExtractedTextFormatter.builder()
                                        .withNumberOfTopTextLinesToDelete(0)
                                        .build())
                        .withPagesPerDocument(1)
                        .build());

        List<Document> pages = reader.read().stream()
                .map(page -> new Document(
                        cleanText(page.getText()),
                        enrichMetadata(page.getMetadata(), fileName, documentId)))
                .filter(page -> {
                    assert page.getText() != null;
                    return isMeaningful(page.getText());
                })
                .toList();

        return dropAnimationDuplicates(pages);
    }

    private String cleanText(String text) {
        if (text == null) return "";
        return text
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n\\s*\\n+", "\n\n")
                .trim();
    }

    // Bỏ trang gần như chỉ có tiêu đề. Ngưỡng 150 chỉnh theo tài liệu của bạn.
    private boolean isMeaningful(String text) {
        long letters = text.chars().filter(Character::isLetter).count();
        return letters >= 150;
    }

    // Nếu trang i giống trang i+1 từ 80% trở lên thì bỏ trang i, giữ trang đầy đủ hơn.
    private List<Document> dropAnimationDuplicates(List<Document> pages) {
        List<Document> result = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++) {
            boolean hasFuller = i + 1 < pages.size()
                    && similarity(Objects.requireNonNull(pages.get(i).getText()), Objects.requireNonNull(pages.get(i + 1).getText())) >= 0.8;
            if (!hasFuller) {
                result.add(pages.get(i));
            }
        }
        return result;
    }

    private double similarity(String a, String b) {
        Set<String> sa = new HashSet<>(Arrays.asList(a.toLowerCase().split("\\s+")));
        Set<String> sb = new HashSet<>(Arrays.asList(b.toLowerCase().split("\\s+")));
        if (sa.isEmpty() || sb.isEmpty()) return 0;
        Set<String> inter = new HashSet<>(sa);
        inter.retainAll(sb);
        Set<String> union = new HashSet<>(sa);
        union.addAll(sb);
        return (double) inter.size() / union.size();
    }

    private List<Document> splitPagesIntoChunks(List<Document> pages) {
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(500)
                .withMinChunkSizeChars(350)
                .withMinChunkLengthToEmbed(100)
                .withMaxNumChunks(10000)
                .withKeepSeparator(true)
                .build();

        return splitter.apply(pages);
    }

    private SearchRequest buildSearchRequest(String query, String documentId, int topK, double threshold) {
        SearchRequest.Builder builder = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(threshold);

        if (documentId != null && !documentId.isBlank()) {
            builder.filterExpression("documentId == '" + escapeFilterValue(documentId) + "'");
        }

        return builder.build();
    }

    private Map<String, Object> enrichMetadata(Map<String, Object> existingMetadata, String fileName, String documentId) {
        Map<String, Object> metadata = new HashMap<>(existingMetadata);
        metadata.put("fileName", fileName);
        metadata.put("documentId", documentId);
        metadata.put("type", "pdf");
        return metadata;
    }

    private PdfSourceDto toSource(Document document) {
        Map<String, Object> metadata = document.getMetadata();

        return new PdfSourceDto(
                document.getText(),
                String.valueOf(metadata.getOrDefault("fileName", "unknown.pdf")),
                String.valueOf(metadata.getOrDefault("documentId", "unknown")),
                String.valueOf(metadata.getOrDefault("page_number", metadata.getOrDefault("page", "unknown"))),
                document.getScore()
        );
    }

    private void validatePdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("PDF file is required");
        }

        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are allowed");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.equalsIgnoreCase("application/pdf")) {
            throw new IllegalArgumentException("Invalid content type. Expected application/pdf");
        }
    }

    private String cleanFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "uploaded.pdf";
        }
        return Path.of(fileName).getFileName().toString();
    }

    private String escapeFilterValue(String value) {
        return value.replace("'", "\\'");
    }

    private void deleteTempFile(Path tempFile) {
        if (tempFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(tempFile);
        }
        catch (IOException ignored) {
            // Temporary file cleanup failure should not fail the user request.
        }
    }
}
