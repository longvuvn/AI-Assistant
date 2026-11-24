package mycomputer.project.ai_assistant.orchestrator.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import mycomputer.project.ai_assistant.core.tools.coding.CodeGenerationService;
import mycomputer.project.ai_assistant.core.tools.filesystem.FileSystemService;
import mycomputer.project.ai_assistant.core.tools.project.ProjectAnalysisService;
import mycomputer.project.ai_assistant.core.tools.testing.TestExecutionService;
import mycomputer.project.ai_assistant.dto.request.orchestrator.PipelineRequest;
import mycomputer.project.ai_assistant.dto.response.orchestrator.PipelineResponse;
import mycomputer.project.ai_assistant.memory.episodic.model.ExperienceRecord;
import mycomputer.project.ai_assistant.memory.episodic.service.AgentExperienceService;
import mycomputer.project.ai_assistant.orchestrator.model.AgentRole;
import mycomputer.project.ai_assistant.orchestrator.model.AgentStage;
import mycomputer.project.ai_assistant.orchestrator.model.PipelineContext;
import mycomputer.project.ai_assistant.orchestrator.model.PipelineStageEvent;
import mycomputer.project.ai_assistant.orchestrator.service.MultiAgentOrchestratorService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class MultiAgentOrchestratorServiceImpl implements MultiAgentOrchestratorService {

    private final ChatClient baAgentClient;
    private final ChatClient architectAgentClient;
    private final ChatClient apiDesignerAgentClient;
    private final ChatClient developerAgentClient;
    private final ChatClient testerAgentClient;
    private final ChatClient securityAgentClient;
    private final ChatClient devopsAgentClient;

    private final FileSystemService fileSystemService;
    private final TestExecutionService testExecutionService;
    private final ProjectAnalysisService projectAnalysisService;
    private final CodeGenerationService codeGenerationService;
    private final AgentExperienceService agentExperienceService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MultiAgentOrchestratorServiceImpl(
            @Qualifier("baAgentClient") ChatClient baAgentClient,
            @Qualifier("architectAgentClient") ChatClient architectAgentClient,
            @Qualifier("apiDesignerAgentClient") ChatClient apiDesignerAgentClient,
            @Qualifier("developerAgentClient") ChatClient developerAgentClient,
            @Qualifier("testerAgentClient") ChatClient testerAgentClient,
            @Qualifier("securityAgentClient") ChatClient securityAgentClient,
            @Qualifier("devopsAgentClient") ChatClient devopsAgentClient,
            FileSystemService fileSystemService,
            TestExecutionService testExecutionService,
            ProjectAnalysisService projectAnalysisService,
            CodeGenerationService codeGenerationService,
            AgentExperienceService agentExperienceService) {

        this.baAgentClient = baAgentClient;
        this.architectAgentClient = architectAgentClient;
        this.apiDesignerAgentClient = apiDesignerAgentClient;
        this.developerAgentClient = developerAgentClient;
        this.testerAgentClient = testerAgentClient;
        this.securityAgentClient = securityAgentClient;
        this.devopsAgentClient = devopsAgentClient;
        this.fileSystemService = fileSystemService;
        this.testExecutionService = testExecutionService;
        this.projectAnalysisService = projectAnalysisService;
        this.codeGenerationService = codeGenerationService;
        this.agentExperienceService = agentExperienceService;
    }

    @Override
    public PipelineResponse executePipeline(PipelineRequest request) {
        String pipelineId = UUID.randomUUID().toString();
        PipelineContext ctx = initContext(pipelineId, request);
        return runPipeline(ctx, null);
    }

    @Override
    public SseEmitter streamPipeline(PipelineRequest request) {
        String pipelineId = UUID.randomUUID().toString();
        PipelineContext ctx = initContext(pipelineId, request);

        // Timeout 15 phút cho quy trình 7 Agent
        SseEmitter emitter = new SseEmitter(15 * 60 * 1000L);
        AtomicBoolean completed = new AtomicBoolean(false);

        Consumer<PipelineStageEvent> eventListener = event -> {
            if (completed.get()) return;
            try {
                emitter.send(SseEmitter.event()
                        .id(UUID.randomUUID().toString())
                        .name("PIPELINE_STAGE")
                        .data(event));
            } catch (Exception ex) {
                log.warn("Không thể gửi SSE event tới client (client có thể đã ngắt kết nối): {}", ex.getMessage());
                completed.set(true);
            }
        };

        emitter.onCompletion(() -> {
            log.info("SSE hoàn thành cho Pipeline ID: {}", pipelineId);
            completed.set(true);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE timeout cho Pipeline ID: {}", pipelineId);
            completed.set(true);
            emitter.complete();
        });
        emitter.onError(e -> {
            log.error("SSE gặp lỗi cho Pipeline ID {}: {}", pipelineId, e.getMessage());
            completed.set(true);
        });

        CompletableFuture.runAsync(() -> {
            try {
                runPipeline(ctx, eventListener);
                if (!completed.get()) {
                    emitter.complete();
                }
            } catch (Exception ex) {
                log.error("Lỗi khi thực thi Pipeline {}: {}", pipelineId, ex.getMessage(), ex);
                if (!completed.get()) {
                    emit(eventListener, PipelineStageEvent.of(pipelineId, 0, AgentRole.BA, AgentStage.FAILED,
                            "Quy trình thất bại: " + ex.getMessage()));
                    emitter.completeWithError(ex);
                }
            }
        });

        return emitter;
    }

    private PipelineContext initContext(String pipelineId, PipelineRequest request) {
        PipelineContext ctx = new PipelineContext();
        ctx.setPipelineId(pipelineId);
        ctx.setUserPrompt(request.message());
        ctx.setWorkspacePath(request.workspacePath());
        ctx.setSubDirectory(request.subDirectory());
        ctx.setStartTime(Instant.now());
        return ctx;
    }

    private PipelineResponse runPipeline(PipelineContext ctx, Consumer<PipelineStageEvent> eventEmitter) {
        if (ctx.getWorkspacePath() == null || ctx.getWorkspacePath().isBlank()) {
            throw new IllegalArgumentException("Đường dẫn workspace (workspacePath) là bắt buộc.");
        }

        String pipelineId = ctx.getPipelineId();
        String targetDir = (ctx.getSubDirectory() != null && !ctx.getSubDirectory().isBlank()) ? ctx.getSubDirectory() : ".";

        try {
            // Khởi tạo workspace bảo mật
            fileSystemService.getSecurityService().setWorkspace(ctx.getWorkspacePath());
            fileSystemService.clearRecentlyModifiedFiles();

            // Khảo sát cấu trúc dự án và quy chuẩn coding style nếu có
            String projectContext = enrichProjectContext(targetDir);

            // =========================================================================
            // STAGE 1: BUSINESS ANALYST (BA)
            // =========================================================================
            log.info("Pipeline [{}]: Bắt đầu Stage 1 (BA Agent)", pipelineId);
            emit(eventEmitter, PipelineStageEvent.of(pipelineId, 1, AgentRole.BA, AgentStage.IN_PROGRESS,
                    "BA Agent đang phân tích yêu cầu nghiệp vụ và xây dựng tài liệu PRD..."));

            String baPrompt = String.format("""
                    YÊU CẦU ĐỀ BÀI TỪ NGƯỜI DÙNG:
                    %s

                    Hãy phân tích chi tiết và xuất bản tài liệu PRD (Product Requirements Document) chuẩn quốc tế.
                    Bao gồm:
                    1. Tổng quan & Mục tiêu dự án.
                    2. Các thực thể chính (Entities) và các thuộc tính bắt buộc.
                    3. Danh sách User Stories chi tiết.
                    4. Quy tắc nghiệp vụ (Business Rules, Validation constraints).
                    5. Tiêu chí chấp nhận (Acceptance Criteria).
                    """, ctx.getUserPrompt());

            String prd = baAgentClient.prompt().user(baPrompt).call().content();
            ctx.setPrdDocument(prd);

            emit(eventEmitter, PipelineStageEvent.of(pipelineId, 1, AgentRole.BA, AgentStage.COMPLETED,
                    "BA Agent đã hoàn thành tài liệu PRD.", Map.of("prd", prd)));

            // =========================================================================
            // STAGE 2: SOLUTION ARCHITECT (ARCHITECT)
            // =========================================================================
            log.info("Pipeline [{}]: Bắt đầu Stage 2 (Architect Agent)", pipelineId);
            emit(eventEmitter, PipelineStageEvent.of(pipelineId, 2, AgentRole.ARCHITECT, AgentStage.IN_PROGRESS,
                    "Architect Agent đang thiết kế sơ đồ kiến trúc hệ thống (ERD, Class, Sequence)..."));

            String architectPrompt = String.format("""
                    BẢN ĐẶC TẢ YÊU CẦU (PRD) TỪ BUSINESS ANALYST:
                    %s

                    Hãy thiết kế kiến trúc hệ thống và vẽ đầy đủ 3 sơ đồ bằng Mermaid.js:
                    1. Database ERD (`erDiagram`): Bảng, PK, FK, kiểu dữ liệu, quan hệ 1-N, N-N.
                    2. Class Diagram (`classDiagram`): Các tầng Model, DTO, Repository, Service, Controller.
                    3. Sequence Diagram (`sequenceDiagram`): Luồng xử lý một request CRUD từ Client qua các tầng và phản hồi.
                    Lưu ý: Cú pháp Mermaid phải tuyệt đối chính xác để client có thể render trực tiếp!
                    """, ctx.getPrdDocument());

            String diagrams = architectAgentClient.prompt().user(architectPrompt).call().content();
            ctx.setMermaidDiagrams(diagrams);

            emit(eventEmitter, PipelineStageEvent.of(pipelineId, 2, AgentRole.ARCHITECT, AgentStage.COMPLETED,
                    "Architect Agent đã hoàn thành thiết kế kiến trúc và sơ đồ Mermaid.js.", Map.of("diagrams", diagrams)));

            // =========================================================================
            // STAGE 3: API DESIGNER (API_DESIGNER)
            // =========================================================================
            log.info("Pipeline [{}]: Bắt đầu Stage 3 (API Designer Agent)", pipelineId);
            emit(eventEmitter, PipelineStageEvent.of(pipelineId, 3, AgentRole.API_DESIGNER, AgentStage.IN_PROGRESS,
                    "API Designer Agent đang thiết kế đặc tả hợp đồng RESTful API và DTOs..."));

            String apiPrompt = String.format("""
                    BẢN ĐẶC TẢ PRD TỪ BA:
                    %s

                    SƠ ĐỒ KIẾN TRÚC TỪ ARCHITECT:
                    %s

                    Hãy thiết kế Bản Đặc Tả API RESTful chuẩn OpenAPI 3.0:
                    1. Base URL: `/api/v1/{resources}` (số nhiều, ví dụ: `/api/v1/students`).
                    2. Cấu trúc chuẩn `ApiResponse<T>`: code, message, data, timestamp.
                    3. Chi tiết từng Endpoint CRUD (GET, GET by ID, POST, PUT, DELETE) kèm HTTP status (200, 201, 204, 400, 404).
                    4. Cấu trúc các DTO: `{Entity}Request` (có jakarta.validation: @NotBlank, @Min, @Email...), `{Entity}Response`.
                    5. Các mã lỗi và kịch bản ngoại lệ chuẩn hóa.
                    """, ctx.getPrdDocument(), ctx.getMermaidDiagrams());

            String apiSpec = apiDesignerAgentClient.prompt().user(apiPrompt).call().content();
            ctx.setApiSpecification(apiSpec);

            emit(eventEmitter, PipelineStageEvent.of(pipelineId, 3, AgentRole.API_DESIGNER, AgentStage.COMPLETED,
                    "API Designer Agent đã hoàn thành đặc tả hợp đồng RESTful API.", Map.of("apiSpec", apiSpec)));

            // =========================================================================
            // STAGE 4: DEVELOPER (DEVELOPER)
            // =========================================================================
            log.info("Pipeline [{}]: Bắt đầu Stage 4 (Developer Agent)", pipelineId);
            emit(eventEmitter, PipelineStageEvent.of(pipelineId, 4, AgentRole.DEVELOPER, AgentStage.IN_PROGRESS,
                    "Developer Agent đang tạo các file mã nguồn Spring Boot 3+ vào workspace..."));

            String devPrompt = String.format("""
                    THÔNG TIN DỰ ÁN TỪ WORKSPACE:
                    %s

                    BẢN ĐẶC TẢ PRD:
                    %s

                    SƠ ĐỒ KIẾN TRÚC:
                    %s

                    ĐẶC TẢ HỢP ĐỒNG API:
                    %s

                    NHIỆM VỤ LẬP TRÌNH:
                    Hãy viết đầy đủ mã nguồn Java cho dự án theo chuẩn Clean Architecture và Spring Boot 3+ (Java 21):
                    1. Model (Entity): Dùng `jakarta.persistence.*`, Lombok `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`.
                    2. DTO: `{Entity}Request` (có jakarta.validation) và `{Entity}Response`.
                    3. Repository: Interface kế thừa `JpaRepository<{Entity}, Long>`.
                    4. Service: Interface `{Entity}Service` và class `{Entity}ServiceImpl` (Dùng constructor injection `@RequiredArgsConstructor`).
                    5. Controller: RESTful Controller tại `/api/v1/{resources}` với đầy đủ các endpoints CRUD.
                    6. Cấu trúc ApiResponse<T> chuẩn nếu chưa có trong dự án.

                    QUY TẮC BẮT BUỘC:
                    - BẮT BUỘC dùng `jakarta.*`, TUYỆT ĐỐI KHÔNG dùng `javax.*`.
                    - Mỗi file mã nguồn phải viết hoàn chỉnh, đặt trong khối markdown code kèm đường dẫn tương đối trong dấu backtick (ví dụ: `src/main/java/.../Student.java`).
                    - Bạn cũng có thể dùng công cụ createFile để ghi trực tiếp xuống đĩa.
                    """, projectContext, ctx.getPrdDocument(), ctx.getMermaidDiagrams(), ctx.getApiSpecification());

            String devAnswer = developerAgentClient.prompt().user(devPrompt).call().content();

            // Trích xuất và lưu tất cả mã nguồn do Developer sinh ra
            extractAndSaveArtifacts(devAnswer, targetDir, eventEmitter, pipelineId);

            List<String> affectedFiles = new ArrayList<>(fileSystemService.getRecentlyModifiedFiles());
            ctx.setSourceFiles(affectedFiles);

            emit(eventEmitter, PipelineStageEvent.of(pipelineId, 4, AgentRole.DEVELOPER, AgentStage.COMPLETED,
                    String.format("Developer Agent đã tạo thành công %d file mã nguồn.", affectedFiles.size()),
                    Map.of("affectedFiles", affectedFiles)));

            // =========================================================================
            // STAGE 5: QA & TESTER (TESTER) - KÈM VÒNG LẶP AUTO-REPAIR
            // =========================================================================
            log.info("Pipeline [{}]: Bắt đầu Stage 5 (Tester Agent)", pipelineId);
            emit(eventEmitter, PipelineStageEvent.of(pipelineId, 5, AgentRole.TESTER, AgentStage.IN_PROGRESS,
                    "Tester Agent đang viết Unit Tests và xác minh biên dịch mã nguồn..."));

            String testerPrompt = String.format("""
                    DANH SÁCH FILE MÃ NGUỒN VỪA ĐƯỢC TẠO:
                    %s

                    ĐẶC TẢ API & YÊU CẦU:
                    %s

                    NHIỆM VỤ:
                    1. Viết Unit Test cho tầng Service (dùng Mockito, JUnit 5) và tầng Controller (@WebMvcTest hoặc Mockito).
                    2. Kiểm tra cả Happy Path và Unhappy Path (Validation Error, 404 Not Found).
                    3. Đặt các file test trong thư mục `src/test/java/.../*Test.java` với đường dẫn trong dấu backtick và khối markdown code hoàn chỉnh.
                    """, String.join("\n", ctx.getSourceFiles()), ctx.getApiSpecification());

            String testerAnswer = testerAgentClient.prompt().user(testerPrompt).call().content();
            extractAndSaveArtifacts(testerAnswer, targetDir, eventEmitter, pipelineId);

            // Xác minh biên dịch & Chạy kiểm thử tự động với Feedback loop
            String testResultsSummary = verifyAndSelfRepair(targetDir, eventEmitter, pipelineId);
            ctx.setTestResults(testResultsSummary);

            emit(eventEmitter, PipelineStageEvent.of(pipelineId, 5, AgentRole.TESTER, AgentStage.COMPLETED,
                    "Tester Agent đã hoàn thành kiểm thử và xác minh biên dịch.", Map.of("testResults", testResultsSummary)));

            // =========================================================================
            // STAGE 6: SECURITY AUDITOR (SECURITY)
            // =========================================================================
            log.info("Pipeline [{}]: Bắt đầu Stage 6 (Security Auditor Agent)", pipelineId);
            emit(eventEmitter, PipelineStageEvent.of(pipelineId, 6, AgentRole.SECURITY, AgentStage.IN_PROGRESS,
                    "Security Auditor đang thẩm định an toàn mã nguồn theo tiêu chuẩn OWASP Top 10..."));

            String secPrompt = String.format("""
                    DANH SÁCH MÃ NGUỒN VỪA ĐƯỢC TẠO:
                    %s

                    ĐẶC TẢ API:
                    %s

                    KẾT QUẢ KIỂM THỬ:
                    %s

                    Hãy thẩm định mã nguồn theo tiêu chuẩn OWASP Top 10:
                    1. SQL Injection: Kiểm tra việc dùng Spring Data JPA / PreparedStatement.
                    2. Mass Assignment: Kiểm tra việc tách riêng Entity và Request DTO trong Controller.
                    3. Input Validation: Đảm bảo có `@Valid` và các ràng buộc dữ liệu `@NotBlank`, `@Size`...
                    4. Path Traversal & File Upload an toàn.
                    5. Information Disclosure: Không để lộ thông tin nhạy cảm qua lỗi.
                    6. Hardcoded Secrets: Không để token, mật khẩu cứng trong code.

                    Xuất Báo Cáo Thẩm Định Bảo Mật (Security Audit Report) gồm:
                    - Đánh giá tổng thể mức độ an toàn (SAFE / WARNING / CRITICAL).
                    - Bảng đánh giá chi tiết từng hạng mục.
                    - Khuyến nghị nâng cao (nếu có).
                    """, String.join("\n", ctx.getSourceFiles()), ctx.getApiSpecification(), ctx.getTestResults());

            String secReport = securityAgentClient.prompt().user(secPrompt).call().content();
            ctx.setSecurityReport(secReport);

            emit(eventEmitter, PipelineStageEvent.of(pipelineId, 6, AgentRole.SECURITY, AgentStage.COMPLETED,
                    "Security Auditor đã hoàn thành thẩm định bảo mật mã nguồn.", Map.of("securityReport", secReport)));

            // =========================================================================
            // STAGE 7: DEVOPS ENGINEER (DEVOPS)
            // =========================================================================
            log.info("Pipeline [{}]: Bắt đầu Stage 7 (DevOps Agent)", pipelineId);
            emit(eventEmitter, PipelineStageEvent.of(pipelineId, 7, AgentRole.DEVOPS, AgentStage.IN_PROGRESS,
                    "DevOps Agent đang tạo Dockerfile Multi-stage và docker-compose.yml..."));

            String devopsPrompt = String.format("""
                    THÔNG TIN DỰ ÁN SPRING BOOT:
                    %s

                    ĐẶC TẢ API & CÁC THỰC THỂ:
                    %s

                    NHIỆM VỤ:
                    1. Tạo file `Dockerfile` chuẩn Production:
                       - Multi-stage build (Stage 1: maven:3.9-eclipse-temurin-21 build jar; Stage 2: eclipse-temurin:21-jre-alpine).
                       - Tạo non-root user `spring` để chạy an toàn.
                       - Expose port 8080/8082.
                    2. Tạo file `docker-compose.yml`:
                       - Service ứng dụng Spring Boot liên kết với Database (PostgreSQL hoặc MySQL).
                       - Định nghĩa biến môi trường SPRING_DATASOURCE_URL, USERNAME, PASSWORD.
                       - Cấu hình healthcheck cho database và app (/actuator/health).
                    3. Tạo file `.dockerignore`: Loại trừ target/, .git/, .idea/.

                    BẮT BUỘC:
                    Đặt mỗi file với đường dẫn tương đối trong dấu backtick (ví dụ: `Dockerfile`, `docker-compose.yml`, `.dockerignore`) kèm khối code markdown hoàn chỉnh để hệ thống tự động ghi xuống đĩa.
                    """, projectContext, ctx.getApiSpecification());

            String devopsAnswer = devopsAgentClient.prompt().user(devopsPrompt).call().content();
            extractAndSaveArtifacts(devopsAnswer, targetDir, eventEmitter, pipelineId);
            ctx.setDevopsArtifacts(devopsAnswer);

            emit(eventEmitter, PipelineStageEvent.of(pipelineId, 7, AgentRole.DEVOPS, AgentStage.COMPLETED,
                    "DevOps Agent đã cấu hình xong Dockerfile và docker-compose.yml.", Map.of("devops", devopsAnswer)));

            // =========================================================================
            // HOÀN THÀNH TOÀN BỘ QUY TRÌNH (SDLC PIPELINE FINISHED)
            // =========================================================================
            List<String> allModifiedFiles = fileSystemService.getRecentlyModifiedFiles();
            ctx.setSourceFiles(allModifiedFiles);
            ctx.setSuccess(true);
            ctx.setEndTime(Instant.now());

            String summary = String.format("Quy trình 7 Agent SDLC hoàn tất thành công! Đã tạo %d files, kiểm thử và cấu hình container đầy đủ.",
                    allModifiedFiles.size());

            emit(eventEmitter, PipelineStageEvent.of(pipelineId, 7, AgentRole.DEVOPS, AgentStage.COMPLETED,
                    "Quy trình Multi-Agent Pipeline đã hoàn thành xuất sắc!",
                    Map.of("summary", summary, "totalFiles", allModifiedFiles.size())));

            log.info("Pipeline [{}] hoàn thành xuất sắc: {} files", pipelineId, allModifiedFiles.size());
            return PipelineResponse.from(ctx, summary);

        } finally {
            // Luôn dọn dẹp workspace context để tránh rò rỉ ThreadLocal
            fileSystemService.getSecurityService().clearWorkspace();
        }
    }

    private String verifyAndSelfRepair(String targetDir, Consumer<PipelineStageEvent> eventEmitter, String pipelineId) {
        StringBuilder sb = new StringBuilder();

        // 1. Kiểm tra xem dự án có pom.xml để biên dịch không
        boolean hasPom = fileSystemService.exists(targetDir + "/pom.xml") || fileSystemService.exists("pom.xml");
        if (!hasPom) {
            return "Dự án không có pom.xml trong workspace. Đã bỏ qua bước chạy Maven compile/test.";
        }

        // 2. Chạy thử nghiệm biên dịch Maven
        TestExecutionService.TestResult compileResult = testExecutionService.runMavenCompile(targetDir);

        int maxRetries = 2;
        int currentRetry = 0;

        while (!compileResult.passed() && currentRetry < maxRetries) {
            currentRetry++;
            String cleanErrors = extractMavenErrors(compileResult.details());
            log.warn("Lỗi biên dịch Maven tại '{}' (lần {}/{}):\n{}", targetDir, currentRetry, maxRetries, cleanErrors);

            emit(eventEmitter, PipelineStageEvent.of(pipelineId, 5, AgentRole.TESTER, AgentStage.IN_PROGRESS,
                    String.format("Phát hiện lỗi biên dịch Maven (lần %d/%d). Đang yêu cầu Developer Agent tự động sửa lỗi...", currentRetry, maxRetries),
                    Map.of("errors", cleanErrors)));

            // 2.1. Truy hồi kinh nghiệm sửa lỗi tương tự từ pgvector (Episodic Memory)
            List<ExperienceRecord> similarLessons = agentExperienceService.findSimilarExperiences(cleanErrors, 2, 0.6);
            String lessonsPrompt = "";
            if (!similarLessons.isEmpty()) {
                lessonsPrompt = "\n" + agentExperienceService.formatForPrompt(similarLessons);
                emit(eventEmitter, PipelineStageEvent.of(pipelineId, 5, AgentRole.TESTER, AgentStage.IN_PROGRESS,
                        String.format("Episodic Memory: Đã tìm thấy %d bài học sửa lỗi tương tự từ pgvector.", similarLessons.size()),
                        Map.of("lessonsFound", similarLessons.size())));
            }

            String fixPrompt = String.format("""
                    PHÁT HIỆN LỖI BIÊN DỊCH MAVEN KHI KIỂM TRA MÃ NGUỒN:
                    %s

                    CHI TIẾT LỖI TỪ TRÌNH BIÊN DỊCH:
                    %s
                    %s

                    HƯỚNG DẪN SỬA LỖI CHO DEVELOPER:
                    1. Đọc kỹ file bị lỗi và dòng gây lỗi.
                    2. Đảm bảo import `jakarta.*` (tuyệt đối KHÔNG dùng `javax.*`).
                    3. Đảm bảo khai báo đầy đủ annotations, constructors và kiểu dữ liệu.
                    4. Viết lại TOÀN BỘ file bị lỗi, đặt trong khối markdown code kèm đường dẫn file trong dấu backtick (`src/main/java/...`).
                    """, compileResult.summary(), cleanErrors, lessonsPrompt);

            String fixAnswer = developerAgentClient.prompt().user(fixPrompt).call().content();
            extractAndSaveArtifacts(fixAnswer, targetDir, eventEmitter, pipelineId);

            // Biên dịch lại sau khi sửa
            compileResult = testExecutionService.runMavenCompile(targetDir);

            // Nếu sửa thành công: Tự động lưu trải nghiệm vào pgvector (Episodic Memory)
            if (compileResult.passed()) {
                try {
                    ExperienceRecord exp = new ExperienceRecord(
                            UUID.randomUUID().toString(),
                            "MAVEN_COMPILE_ERROR",
                            cleanErrors.length() > 120 ? cleanErrors.substring(0, 120).trim() : cleanErrors.trim(),
                            cleanErrors,
                            "Developer Agent đã tự động sửa lỗi thành công và vượt qua kiểm tra biên dịch Maven",
                            fixAnswer,
                            targetDir,
                            "Tự động sửa lỗi biên dịch dự án Maven",
                            List.of("maven", "compile-repair", "spring-boot-3")
                    );
                    agentExperienceService.recordExperience(exp);
                    emit(eventEmitter, PipelineStageEvent.of(pipelineId, 5, AgentRole.TESTER, AgentStage.IN_PROGRESS,
                            "Episodic Memory: Đã tự động lưu ca sửa lỗi thành công vào pgvector để tích lũy kinh nghiệm."));
                } catch (Exception ex) {
                    log.warn("Episodic Memory: Không thể lưu kinh nghiệm: {}", ex.getMessage());
                }
            }
        }

        if (compileResult.passed()) {
            sb.append("✓ BIÊN DỊCH MAVEN (BUILD SUCCESS): Toàn bộ mã nguồn đã biên dịch thành công!\n");
        } else {
            sb.append("✗ CẢNH BÁO BIÊN DỊCH: Vẫn còn lỗi biên dịch chưa được giải quyết hoàn toàn:\n")
                    .append(extractMavenErrors(compileResult.details())).append("\n");
        }

        // 3. Chạy Unit Tests
        try {
            TestExecutionService.TestResult testResult = testExecutionService.runMavenTests(targetDir, null);
            sb.append("\nKẾT QUẢ CHẠY UNIT TEST:\n").append(testResult.toFormattedString());
        } catch (Exception ex) {
            sb.append("\nLưu ý kiểm thử: ").append(ex.getMessage());
        }

        return sb.toString();
    }

    private String enrichProjectContext(String targetDir) {
        StringBuilder sb = new StringBuilder();
        try {
            // Nạp quy chuẩn CODING_STYLE.md nếu có
            String codingStyle = detectAndReadCodingStyle(targetDir);
            if (codingStyle != null) {
                sb.append("\n--- QUY CHUẨN LẬP TRÌNH CỦA DỰ ÁN (CODING STYLE) ---\n")
                        .append(codingStyle).append("\n")
                        .append("----------------------------------------------------\n");
            }

            // Nạp cấu trúc dự án Spring Boot hoặc cây thư mục
            String springBootInfo = projectAnalysisService.analyzeSpringBootProject(targetDir);
            if (springBootInfo != null) {
                sb.append(springBootInfo);
            } else {
                String tree = projectAnalysisService.getProjectTree(targetDir, 5);
                String projectType = projectAnalysisService.detectProjectType(targetDir);
                sb.append("\n--- CẤU TRÚC THƯ MỤC DỰ ÁN ---\n")
                        .append(projectType).append("\n")
                        .append(tree).append("\n")
                        .append("-------------------------------\n");
            }
        } catch (Exception e) {
            log.warn("Lỗi khi khảo sát dự án tại '{}': {}", targetDir, e.getMessage());
        }
        return sb.toString();
    }

    private String detectAndReadCodingStyle(String targetDir) {
        List<String> candidatePaths = List.of(
                targetDir + "/CODING_STYLE.md",
                targetDir + "/coding_style.md",
                targetDir + "/CONVENTIONS.md",
                targetDir + "/conventions.md",
                "CODING_STYLE.md",
                "CONVENTIONS.md"
        );

        for (String path : candidatePaths) {
            try {
                if (fileSystemService.exists(path)) {
                    String content = fileSystemService.read(path);
                    if (content != null && !content.isBlank()) {
                        return content.trim();
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private void extractAndSaveArtifacts(String content, String subDir, Consumer<PipelineStageEvent> eventEmitter, String pipelineId) {
        if (content == null || content.isBlank()) return;

        // 1. Thử parse tool call dạng JSON trước nếu AI sinh ra tool call JSON
        tryParseAndExecuteJsonTools(content, subDir, eventEmitter, pipelineId);

        // 2. Trích xuất các khối code block trong Markdown (hỗ trợ Java, Dockerfile, YAML, Dockerignore, etc.)
        String textToParse = content;
        int countBackticks = 0;
        int idx = 0;
        while ((idx = textToParse.indexOf("```", idx)) != -1) {
            countBackticks++;
            idx += 3;
        }
        if (countBackticks % 2 != 0) {
            textToParse = textToParse + "\n```";
        }

        Pattern codeBlockPattern = Pattern.compile("```(?:java|dockerfile|yaml|yml|properties|xml)?\\s*\\n([\\s\\S]*?)\\n```");
        Matcher matcher = codeBlockPattern.matcher(textToParse);

        int lastEnd = 0;
        while (matcher.find()) {
            String code = matcher.group(1).trim();
            int blockStart = matcher.start();
            String precedingText = content.substring(lastEnd, blockStart);
            lastEnd = matcher.end();

            if (code.startsWith("{") && code.endsWith("}")) continue; // Bỏ qua JSON nếu có
            if (code.length() < 10) continue;

            String filePath = null;

            // Tìm đường dẫn được bao bởi dấu backtick trước khối code
            Matcher pathMatcher = Pattern.compile("`([a-zA-Z0-9_./\\\\-]+\\.(java|xml|properties|yml|yaml|txt|md|dockerignore)|Dockerfile|docker-compose\\.yml|docker-compose\\.yaml)`").matcher(precedingText);
            String lastFoundPath = null;
            while (pathMatcher.find()) {
                lastFoundPath = pathMatcher.group(1);
            }
            if (lastFoundPath != null) {
                filePath = lastFoundPath;
            }

            // Tự suy luận đường dẫn nếu không có trong backtick
            if (filePath == null) {
                if (code.contains("class ") || code.contains("interface ") || code.contains("enum ") || code.contains("record ")) {
                    Matcher pkgMatcher = Pattern.compile("package\\s+([a-zA-Z0-9_.]+);").matcher(code);
                    Matcher clsMatcher = Pattern.compile("(?:public\\s+)?(?:class|interface|enum|record)\\s+([a-zA-Z0-9_]+)").matcher(code);
                    if (pkgMatcher.find() && clsMatcher.find()) {
                        String pkg = pkgMatcher.group(1);
                        String cls = clsMatcher.group(1);
                        boolean isTest = cls.endsWith("Test") || code.contains("@Test");
                        String rootSrc = isTest ? "src/test/java/" : "src/main/java/";
                        filePath = rootSrc + pkg.replace('.', '/') + "/" + cls + ".java";
                    }
                } else if (code.contains("spring.datasource") || code.contains("spring.jpa")) {
                    filePath = "src/main/resources/application.properties";
                } else if (code.contains("spring:") && (code.contains("datasource:") || code.contains("jpa:"))) {
                    filePath = "src/main/resources/application.yaml";
                } else if (code.contains("<project") && code.contains("<modelVersion>")) {
                    filePath = "pom.xml";
                } else if (code.contains("FROM ") && code.contains("EXPOSE ")) {
                    filePath = "Dockerfile";
                } else if ((code.contains("services:") || code.contains("version:")) && code.contains("image:")) {
                    filePath = "docker-compose.yml";
                }
            }

            // Lưu file xuống workspace
            if (filePath != null) {
                if ((filePath.endsWith("application.properties") || filePath.endsWith("application.yaml") || filePath.endsWith("application.yml"))
                        && !filePath.contains("src/main/resources")) {
                    filePath = "src/main/resources/" + filePath;
                }
                String finalPath = filePath.replace('\\', '/');
                while (finalPath.startsWith("/")) {
                    finalPath = finalPath.substring(1);
                }
                if (subDir != null && !subDir.isBlank() && !subDir.equals(".") && !finalPath.startsWith(subDir)) {
                    finalPath = subDir + "/" + finalPath;
                }

                try {
                    fileSystemService.create(finalPath, code);
                    log.info("Pipeline đã tự động lưu artifact: {}", finalPath);
                    emit(eventEmitter, PipelineStageEvent.of(pipelineId, 4, AgentRole.DEVELOPER, AgentStage.IN_PROGRESS,
                            "Đã ghi file: " + finalPath, Map.of("file", finalPath)));
                } catch (Exception ex) {
                    log.warn("Không thể lưu file '{}': {}", finalPath, ex.getMessage());
                }
            }
        }
    }

    private void tryParseAndExecuteJsonTools(String text, String subDir, Consumer<PipelineStageEvent> eventEmitter, String pipelineId) {
        if (!text.contains("createFile") && !text.contains("updateFile")) return;
        try {
            Matcher m = Pattern.compile("\\{[\\s\\S]*?\"tool\"\\s*:\\s*\"(createFile|updateFile)\"[\\s\\S]*?\\}").matcher(text);
            while (m.find()) {
                String json = m.group();
                JsonNode root = objectMapper.readTree(json);
                String tool = root.path("tool").asText();
                String path = root.path("path").asText();
                String content = root.path("content").asText();

                if (!path.isBlank() && !content.isBlank()) {
                    String finalPath = path.replace('\\', '/');
                    if (subDir != null && !subDir.isBlank() && !subDir.equals(".") && !finalPath.startsWith(subDir)) {
                        finalPath = subDir + "/" + finalPath;
                    }
                    fileSystemService.create(finalPath, content);
                    emit(eventEmitter, PipelineStageEvent.of(pipelineId, 4, AgentRole.DEVELOPER, AgentStage.IN_PROGRESS,
                            "Tool " + tool + " ghi file: " + finalPath, Map.of("file", finalPath)));
                }
            }
        } catch (Exception ignored) {}
    }

    private String extractMavenErrors(String details) {
        if (details == null || details.isBlank()) return "Không có thông tin chi tiết.";
        StringBuilder sb = new StringBuilder();
        for (String line : details.split("\\r?\\n")) {
            String trimmed = line.trim();
            if (trimmed.contains("[ERROR]") || trimmed.contains("error:") || trimmed.contains("cannot find symbol")
                    || trimmed.contains("does not exist") || trimmed.contains("Fatal error") || trimmed.contains("COMPILATION ERROR")) {
                sb.append(trimmed).append("\n");
            }
        }
        return sb.length() > 0 ? sb.toString().trim() : (details.length() > 800 ? details.substring(0, 800) + "..." : details.trim());
    }

    private void emit(Consumer<PipelineStageEvent> emitter, PipelineStageEvent event) {
        if (emitter != null) {
            try {
                emitter.accept(event);
            } catch (Exception ex) {
                log.warn("Lỗi khi gửi sự kiện SSE tới listener: {}", ex.getMessage());
            }
        }
    }
}
