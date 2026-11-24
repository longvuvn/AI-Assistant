package mycomputer.project.ai_assistant;

import mycomputer.project.ai_assistant.memory.episodic.model.ExperienceRecord;
import mycomputer.project.ai_assistant.memory.episodic.service.impl.AgentExperienceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentExperienceServiceTest {

    private VectorStore vectorStore;
    private JdbcTemplate jdbcTemplate;
    private AgentExperienceServiceImpl experienceService;

    @BeforeEach
    void setUp() {
        vectorStore = mock(VectorStore.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        experienceService = new AgentExperienceServiceImpl(vectorStore, jdbcTemplate);
    }

    @Test
    void testRecordExperience() {
        ExperienceRecord exp = new ExperienceRecord(
                "exp-1",
                "MAVEN_COMPILE_ERROR",
                "package javax.persistence does not exist",
                "[ERROR] package javax.persistence does not exist",
                "Chuyển từ javax.persistence.* sang jakarta.persistence.*",
                "import jakarta.persistence.*;",
                "Student.java",
                "Xây dựng Entity sinh viên",
                List.of("maven", "jakarta")
        );

        ExperienceRecord result = experienceService.recordExperience(exp);
        assertNotNull(result);
        assertEquals("exp-1", result.id());
        assertEquals("MAVEN_COMPILE_ERROR", result.errorType());

        // Verify document added to VectorStore
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> captor = ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(1)).add(captor.capture());

        List<Document> savedDocs = captor.getValue();
        assertEquals(1, savedDocs.size());
        Document doc = savedDocs.get(0);
        assertEquals("exp-1", doc.getId());
        assertTrue(doc.getText().contains("javax.persistence"));
        assertEquals("agent_experience", doc.getMetadata().get("type"));
        assertEquals("Student.java", doc.getMetadata().get("affectedFile"));
    }

    @Test
    void testFindSimilarExperiences() {
        Document mockDoc = new Document(
                "exp-2",
                "Lỗi biên dịch javax",
                Map.of(
                        "type", "agent_experience",
                        "experienceId", "exp-2",
                        "errorType", "MAVEN_COMPILE_ERROR",
                        "errorSummary", "Lỗi package javax",
                        "solutionSummary", "Đổi sang jakarta.*",
                        "distance", 0.15
                )
        );

        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(mockDoc));

        List<ExperienceRecord> results = experienceService.findSimilarExperiences("lỗi javax.persistence", 2, 0.6);
        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("exp-2", results.get(0).id());
        assertEquals("Đổi sang jakarta.*", results.get(0).solutionSummary());
        assertNotNull(results.get(0).similarityScore());
        assertTrue(results.get(0).similarityScore() > 0.8);
    }

    @Test
    void testFormatForPrompt() {
        ExperienceRecord exp = new ExperienceRecord(
                "exp-1",
                "MAVEN_COMPILE_ERROR",
                "package javax.persistence does not exist",
                "cannot find symbol",
                "Dùng jakarta.persistence.* trên Spring Boot 3+",
                "import jakarta.persistence.Entity;",
                "Product.java",
                "Tạo entity sản phẩm",
                List.of("maven"),
                Instant.now(),
                0.92
        );

        String prompt = experienceService.formatForPrompt(List.of(exp));
        assertNotNull(prompt);
        assertTrue(prompt.contains("EPISODIC MEMORY"));
        assertTrue(prompt.contains("package javax.persistence does not exist"));
        assertTrue(prompt.contains("Dùng jakarta.persistence.* trên Spring Boot 3+"));
        assertTrue(prompt.contains("92.0%"));
    }

    @Test
    void testDeleteExperience() {
        when(jdbcTemplate.update(anyString(), eq("exp-99"), eq("exp-99"))).thenReturn(1);
        experienceService.deleteExperience("exp-99");
        verify(jdbcTemplate, times(1)).update(anyString(), eq("exp-99"), eq("exp-99"));
    }

    @Test
    void testCountExperiences() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(5L);
        long count = experienceService.countExperiences();
        assertEquals(5L, count);
    }
}
