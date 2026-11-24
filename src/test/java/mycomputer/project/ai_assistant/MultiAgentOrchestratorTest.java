package mycomputer.project.ai_assistant;

import mycomputer.project.ai_assistant.core.tools.coding.CodeGenerationService;
import mycomputer.project.ai_assistant.core.tools.filesystem.FileSystemService;
import mycomputer.project.ai_assistant.core.tools.filesystem.WorkspaceSecurityService;
import mycomputer.project.ai_assistant.core.tools.project.ProjectAnalysisService;
import mycomputer.project.ai_assistant.core.tools.terminal.TerminalService;
import mycomputer.project.ai_assistant.core.tools.testing.TestExecutionService;
import mycomputer.project.ai_assistant.dto.request.orchestrator.PipelineRequest;
import mycomputer.project.ai_assistant.dto.response.orchestrator.PipelineResponse;
import mycomputer.project.ai_assistant.memory.episodic.service.AgentExperienceService;
import mycomputer.project.ai_assistant.orchestrator.model.AgentRole;
import mycomputer.project.ai_assistant.orchestrator.model.AgentStage;
import mycomputer.project.ai_assistant.orchestrator.model.PipelineContext;
import mycomputer.project.ai_assistant.orchestrator.model.PipelineStageEvent;
import mycomputer.project.ai_assistant.orchestrator.service.impl.MultiAgentOrchestratorServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MultiAgentOrchestratorTest {

    @TempDir
    Path tempDir;

    private ChatClient baClient;
    private ChatClient architectClient;
    private ChatClient apiDesignerClient;
    private ChatClient developerClient;
    private ChatClient testerClient;
    private ChatClient securityClient;
    private ChatClient devopsClient;

    private FileSystemService fileSystemService;
    private TestExecutionService testExecutionService;
    private ProjectAnalysisService projectAnalysisService;
    private CodeGenerationService codeGenerationService;
    private AgentExperienceService agentExperienceService;
    private MultiAgentOrchestratorServiceImpl orchestratorService;

    @BeforeEach
    void setUp() {
        WorkspaceSecurityService securityService = new WorkspaceSecurityService();
        securityService.setWorkspace(tempDir.toString());
        fileSystemService = new FileSystemService(securityService);
        TerminalService terminalService = new TerminalService(securityService);
        testExecutionService = mock(TestExecutionService.class);
        projectAnalysisService = mock(ProjectAnalysisService.class);
        codeGenerationService = mock(CodeGenerationService.class);
        agentExperienceService = mock(AgentExperienceService.class);

        // Mocks for 7 agents with deep stubs for ChatClient.prompt().user(...).call().content()
        baClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        architectClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        apiDesignerClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        developerClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        testerClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        securityClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        devopsClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);

        orchestratorService = new MultiAgentOrchestratorServiceImpl(
                baClient, architectClient, apiDesignerClient, developerClient,
                testerClient, securityClient, devopsClient,
                fileSystemService, testExecutionService, projectAnalysisService, codeGenerationService,
                agentExperienceService
        );
    }

    @Test
    void testPipelineStageEventModel() {
        PipelineStageEvent event = PipelineStageEvent.of("pipe-123", 1, AgentRole.BA, AgentStage.IN_PROGRESS, "Đang phân tích");
        assertEquals("pipe-123", event.pipelineId());
        assertEquals(1, event.stageNumber());
        assertEquals(7, event.totalStages());
        assertEquals(AgentRole.BA, event.role());
        assertEquals(AgentStage.IN_PROGRESS, event.stageStatus());
        assertEquals("Đang phân tích", event.message());
        assertNotNull(event.timestamp());
    }

    @Test
    void testPipelineResponseFromContext() {
        PipelineContext ctx = new PipelineContext();
        ctx.setPipelineId("test-id");
        ctx.setSuccess(true);
        ctx.setPrdDocument("PRD content");
        ctx.setMermaidDiagrams("Mermaid content");
        ctx.setApiSpecification("API Spec content");
        ctx.setSourceFiles(List.of("Student.java", "StudentService.java"));
        ctx.setTestResults("BUILD SUCCESS");
        ctx.setSecurityReport("OWASP SAFE");
        ctx.setDevopsArtifacts("Dockerfile content");

        PipelineResponse response = PipelineResponse.from(ctx, "Hoàn thành 7 agent");
        assertTrue(response.success());
        assertEquals("test-id", response.pipelineId());
        assertEquals(2, response.affectedFiles().size());
        assertEquals("PRD content", response.prdDocument());
        assertEquals("Mermaid content", response.mermaidDiagrams());
        assertEquals("API Spec content", response.apiSpecification());
        assertEquals("BUILD SUCCESS", response.testResults());
        assertEquals("OWASP SAFE", response.securityReport());
        assertEquals("Dockerfile content", response.devopsArtifacts());
    }

    @Test
    void testExecutePipelineFullLifecycle() {
        // Setup Agent responses
        when(baClient.prompt().user(anyString()).call().content())
                .thenReturn("# PRD: Quản lý sinh viên\n## Entities\n- Student: id, name, email");

        when(architectClient.prompt().user(anyString()).call().content())
                .thenReturn("```mermaid\nerDiagram\n  STUDENTS ||--o{ ENROLLMENTS : has\n```");

        when(apiDesignerClient.prompt().user(anyString()).call().content())
                .thenReturn("## RESTful API Spec\n- GET /api/v1/students\n- POST /api/v1/students");

        String javaCode = """
                ```java
                package com.example.demo.model;
                public class Student {
                    private Long id;
                    private String name;
                }
                ```
                """;
        when(developerClient.prompt().user(anyString()).call().content())
                .thenReturn(javaCode);

        String testCode = """
                ```java
                package com.example.demo.service;
                public class StudentServiceTest {
                    void testCreate() {}
                }
                ```
                """;
        when(testerClient.prompt().user(anyString()).call().content())
                .thenReturn(testCode);

        when(testExecutionService.runMavenCompile(anyString()))
                .thenReturn(new TestExecutionService.TestResult(true, 0, 0, 0, "BUILD SUCCESS", "All compile passed"));
        when(testExecutionService.runMavenTests(anyString(), isNull()))
                .thenReturn(new TestExecutionService.TestResult(true, 1, 0, 0, "TESTS PASSED", "1 test executed successfully"));

        when(securityClient.prompt().user(anyString()).call().content())
                .thenReturn("## Security Audit: SAFE (OWASP Top 10 Compliant)");

        String devopsContent = """
                `Dockerfile`
                ```dockerfile
                FROM eclipse-temurin:21-jre-alpine
                EXPOSE 8082
                ```
                `docker-compose.yml`
                ```yaml
                version: '3.8'
                services:
                  app:
                    image: my-app
                ```
                """;
        when(devopsClient.prompt().user(anyString()).call().content())
                .thenReturn(devopsContent);

        // Execute pipeline
        PipelineRequest request = new PipelineRequest("Xây dựng module quản lý sinh viên", tempDir.toString());
        PipelineResponse response = orchestratorService.executePipeline(request);

        // Assertions
        assertNotNull(response);
        assertTrue(response.success());
        assertNotNull(response.prdDocument());
        assertTrue(response.prdDocument().contains("Quản lý sinh viên"));
        assertNotNull(response.mermaidDiagrams());
        assertTrue(response.mermaidDiagrams().contains("erDiagram"));
        assertNotNull(response.apiSpecification());
        assertTrue(response.apiSpecification().contains("/api/v1/students"));
        assertNotNull(response.securityReport());
        assertTrue(response.securityReport().contains("SAFE"));
        assertNotNull(response.devopsArtifacts());
        assertTrue(response.devopsArtifacts().contains("Dockerfile"));

        // Verify that files were extracted and saved to disk
        assertTrue(java.nio.file.Files.exists(tempDir.resolve("src/main/java/com/example/demo/model/Student.java")));
        assertTrue(java.nio.file.Files.exists(tempDir.resolve("src/test/java/com/example/demo/service/StudentServiceTest.java")));
        assertTrue(java.nio.file.Files.exists(tempDir.resolve("Dockerfile")));
        assertTrue(java.nio.file.Files.exists(tempDir.resolve("docker-compose.yml")));
    }
}
