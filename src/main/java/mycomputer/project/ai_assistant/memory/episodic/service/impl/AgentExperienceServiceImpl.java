package mycomputer.project.ai_assistant.memory.episodic.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mycomputer.project.ai_assistant.memory.episodic.model.ExperienceRecord;
import mycomputer.project.ai_assistant.memory.episodic.service.AgentExperienceService;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentExperienceServiceImpl implements AgentExperienceService {

    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public ExperienceRecord recordExperience(ExperienceRecord experience) {
        String id = (experience.id() != null && !experience.id().isBlank())
                ? experience.id()
                : UUID.randomUUID().toString();

        Instant now = experience.createdAt() != null ? experience.createdAt() : Instant.now();

        // Xây dựng nội dung văn bản chuẩn để mô hình Embedding (bge-m3) số hóa ngữ nghĩa
        String content = String.format("""
                [LỖI GẶP PHẢI / TRƯỜNG HỢP]:
                %s

                [CHI TIẾT LỖI & STACKTRACE]:
                %s

                [NGỮ CẢNH & TỆP TIN LIÊN QUAN]:
                %s (Tệp: %s)

                [GIẢI PHÁP ĐÃ SỬA THÀNH CÔNG]:
                %s

                [MÃ NGUỒN KHẮC PHỤC]:
                %s
                """,
                nullToEmpty(experience.errorSummary()),
                nullToEmpty(experience.errorDetails()),
                nullToEmpty(experience.taskContext()),
                nullToEmpty(experience.affectedFile()),
                nullToEmpty(experience.solutionSummary()),
                nullToEmpty(experience.fixedCodeSnippet())
        ).trim();

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("type", "agent_experience");
        metadata.put("experienceId", id);
        metadata.put("errorType", nullToEmpty(experience.errorType()));
        metadata.put("errorSummary", nullToEmpty(experience.errorSummary()));
        metadata.put("affectedFile", nullToEmpty(experience.affectedFile()));
        metadata.put("solutionSummary", nullToEmpty(experience.solutionSummary()));
        metadata.put("fixedCodeSnippet", nullToEmpty(experience.fixedCodeSnippet()));
        metadata.put("taskContext", nullToEmpty(experience.taskContext()));
        metadata.put("tags", experience.tags() != null ? String.join(",", experience.tags()) : "");
        metadata.put("createdAt", now.toString());

        Document doc = new Document(id, content, metadata);

        try {
            vectorStore.add(List.of(doc));
            log.info("Episodic Memory: Đã lưu thành công 1 kinh nghiệm sửa lỗi vào pgvector với ID: {}", id);
        } catch (Exception ex) {
            log.error("Lỗi khi ghi kinh nghiệm vào vectorStore: {}", ex.getMessage(), ex);
            throw new RuntimeException("Không thể lưu kinh nghiệm vào vector store: " + ex.getMessage(), ex);
        }

        return new ExperienceRecord(
                id,
                experience.errorType(),
                experience.errorSummary(),
                experience.errorDetails(),
                experience.solutionSummary(),
                experience.fixedCodeSnippet(),
                experience.affectedFile(),
                experience.taskContext(),
                experience.tags(),
                now,
                1.0
        );
    }

    @Override
    public List<ExperienceRecord> findSimilarExperiences(String query, int topK, double threshold) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }

        int safeTopK = topK > 0 ? topK : 3;
        double safeThreshold = threshold > 0 ? threshold : 0.5;

        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(safeTopK)
                    .similarityThreshold(safeThreshold)
                    .filterExpression("type == 'agent_experience'")
                    .build();

            List<Document> matchedDocs = vectorStore.similaritySearch(searchRequest);
            return matchedDocs.stream()
                    .map(this::toExperienceRecord)
                    .toList();
        } catch (Exception ex) {
            log.warn("Episodic Memory: Không thể tìm kiếm kinh nghiệm tương tự: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<ExperienceRecord> getRecentExperiences(int limit) {
        int safeLimit = limit > 0 ? limit : 10;
        String sql = """
                SELECT id, content, metadata
                FROM vector_store
                WHERE metadata->>'type' = 'agent_experience'
                ORDER BY COALESCE((metadata->>'createdAt')::timestamptz, NOW()) DESC
                LIMIT ?
                """;

        try {
            return jdbcTemplate.query(sql, (rs, rowNum) -> {
                String id = rs.getString("id");
                String content = rs.getString("content");
                String metaJson = rs.getString("metadata");

                Map<String, Object> metadata = parseMetadataJson(metaJson);
                Document doc = new Document(id, content, metadata);
                return toExperienceRecord(doc);
            }, safeLimit);
        } catch (Exception ex) {
            log.warn("Episodic Memory: Lỗi khi truy vấn getRecentExperiences: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void deleteExperience(String experienceId) {
        if (experienceId == null || experienceId.isBlank()) return;

        try {
            int rows = jdbcTemplate.update(
                    "DELETE FROM vector_store WHERE metadata->>'experienceId' = ? OR id::text = ?",
                    experienceId, experienceId
            );
            log.info("Episodic Memory: Đã xóa {} bản ghi kinh nghiệm với ID: {}", rows, experienceId);
        } catch (Exception ex) {
            log.error("Lỗi khi xóa kinh nghiệm: {}", ex.getMessage(), ex);
            throw new RuntimeException("Không thể xóa kinh nghiệm: " + ex.getMessage(), ex);
        }
    }

    @Override
    public long countExperiences() {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM vector_store WHERE metadata->>'type' = 'agent_experience'",
                    Long.class
            );
            return count != null ? count : 0L;
        } catch (Exception ex) {
            log.warn("Episodic Memory: Lỗi khi đếm số lượng kinh nghiệm: {}", ex.getMessage());
            return 0L;
        }
    }

    @Override
    public String formatForPrompt(List<ExperienceRecord> experiences) {
        if (experiences == null || experiences.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n========================================================================\n");
        sb.append("BÀI HỌC KINH NGHIỆM ĐÃ TÍCH LŨY TỪ CÁC ĐỒ ÁN TRƯỚC (EPISODIC MEMORY):\n");
        sb.append("Hệ thống đã từng gặp các trường hợp lỗi tương tự và đã sửa thành công như sau:\n");
        sb.append("========================================================================\n");

        int index = 1;
        for (ExperienceRecord exp : experiences) {
            sb.append(String.format("\n--- BÀI HỌC #%d (Độ tương đồng: %.1f%%) ---\n",
                    index++,
                    exp.similarityScore() != null ? exp.similarityScore() * 100 : 90.0));
            sb.append("Lỗi từng gặp: ").append(exp.errorSummary()).append("\n");
            if (exp.errorDetails() != null && !exp.errorDetails().isBlank()) {
                String briefDetails = exp.errorDetails().length() > 300
                        ? exp.errorDetails().substring(0, 300) + "..."
                        : exp.errorDetails();
                sb.append("Chi tiết lỗi:\n").append(briefDetails).append("\n");
            }
            sb.append("Giải pháp đã giải quyết thành công:\n").append(exp.solutionSummary()).append("\n");
            if (exp.fixedCodeSnippet() != null && !exp.fixedCodeSnippet().isBlank()) {
                sb.append("Mã nguồn mẫu đã sửa:\n```java\n").append(exp.fixedCodeSnippet().trim()).append("\n```\n");
            }
            sb.append("------------------------------------------------------------------------\n");
        }

        sb.append("--> CHỈ DẪN CHO AGENT: Hãy tham khảo và áp dụng chính xác các giải pháp trên để giải quyết lỗi nhanh nhất!\n");
        sb.append("========================================================================\n");
        return sb.toString();
    }

    private ExperienceRecord toExperienceRecord(Document doc) {
        Map<String, Object> meta = doc.getMetadata();
        String id = String.valueOf(meta.getOrDefault("experienceId", doc.getId()));
        String errorType = String.valueOf(meta.getOrDefault("errorType", "GENERAL_ERROR"));
        String errorSummary = String.valueOf(meta.getOrDefault("errorSummary", ""));
        String solutionSummary = String.valueOf(meta.getOrDefault("solutionSummary", ""));
        String fixedCodeSnippet = String.valueOf(meta.getOrDefault("fixedCodeSnippet", ""));
        String affectedFile = String.valueOf(meta.getOrDefault("affectedFile", ""));
        String taskContext = String.valueOf(meta.getOrDefault("taskContext", ""));
        String tagsStr = String.valueOf(meta.getOrDefault("tags", ""));
        List<String> tags = tagsStr.isBlank() ? List.of() : Arrays.asList(tagsStr.split(","));

        Instant createdAt = Instant.now();
        Object createdObj = meta.get("createdAt");
        if (createdObj != null) {
            try {
                createdAt = Instant.parse(createdObj.toString());
            } catch (Exception ignored) {}
        }

        Double score = null;
        Object scoreObj = meta.get("distance");
        if (scoreObj instanceof Number n) {
            score = 1.0 - n.doubleValue(); // Cosine distance to similarity
        }

        return new ExperienceRecord(
                id,
                errorType,
                errorSummary,
                doc.getText(),
                solutionSummary,
                fixedCodeSnippet,
                affectedFile,
                taskContext,
                tags,
                createdAt,
                score
        );
    }

    private Map<String, Object> parseMetadataJson(String json) {
        if (json == null || json.isBlank()) return new HashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            return new HashMap<>();
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
