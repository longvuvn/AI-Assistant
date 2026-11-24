package mycomputer.project.ai_assistant.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import mycomputer.project.ai_assistant.core.tools.filesystem.FileSystemService;
import mycomputer.project.ai_assistant.core.tools.project.ProjectAnalysisService;
import mycomputer.project.ai_assistant.core.tools.testing.TestExecutionService;
import mycomputer.project.ai_assistant.dto.request.agent.AgentRequest;
import mycomputer.project.ai_assistant.dto.response.agent.AgentEventType;
import mycomputer.project.ai_assistant.dto.response.agent.AgentResponseDto;
import mycomputer.project.ai_assistant.dto.response.agent.AgentTaskEvent;
import mycomputer.project.ai_assistant.memory.episodic.model.ExperienceRecord;
import mycomputer.project.ai_assistant.memory.episodic.service.AgentExperienceService;
import mycomputer.project.ai_assistant.service.AgentService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class AgentServiceImpl implements AgentService {

    private final ChatClient codingChatClient;
    private final FileSystemService fileSystemService;
    private final TestExecutionService testExecutionService;
    private final ProjectAnalysisService projectAnalysisService;
    private final mycomputer.project.ai_assistant.core.tools.coding.CodeGenerationService codeGenerationService;
    private final AgentExperienceService agentExperienceService;

    public AgentServiceImpl(
            @Qualifier("codingChatClient") ChatClient codingChatClient,
            FileSystemService fileSystemService,
            TestExecutionService testExecutionService,
            ProjectAnalysisService projectAnalysisService,
            mycomputer.project.ai_assistant.core.tools.coding.CodeGenerationService codeGenerationService,
            AgentExperienceService agentExperienceService) {

        this.fileSystemService = fileSystemService;
        this.testExecutionService = testExecutionService;
        this.codingChatClient = codingChatClient;
        this.projectAnalysisService = projectAnalysisService;
        this.codeGenerationService = codeGenerationService;
        this.agentExperienceService = agentExperienceService;
    }

    @Override
    public String codingAlgorithm(String message) {
        AgentResponseDto response = createCodingTask(new AgentRequest(message));
        return response.answer();
    }

    @Override
    public AgentResponseDto createCodingTask(AgentRequest request) {
        String convId = UUID.randomUUID().toString();
        return runTask(convId, request, null);
    }

    @Override
    public AgentResponseDto executeCodingTask(String conversationId, AgentRequest request) {
        return runTask(UUID.fromString(conversationId).toString(), request, null);
    }

    @Override
    public SseEmitter streamCodingTask(AgentRequest request) {
        String convId = UUID.randomUUID().toString();
        return streamCodingTask(convId, request);
    }

    @Override
    public SseEmitter streamCodingTask(String conversationId, AgentRequest request) {
        // Timeout 10 phút để đảm bảo các tác vụ sinh mã phức tạp không bị gián đoạn
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        AtomicBoolean completed = new AtomicBoolean(false);

        Consumer<AgentTaskEvent> listener = event -> {
            if (completed.get()) return;
            try {
                emitter.send(SseEmitter.event()
                        .id(UUID.randomUUID().toString())
                        .name(event.type().name())
                        .data(event));
            } catch (Exception ex) {
                log.warn("Không thể gửi SSE event tới client (có thể client đã đóng kết nối): {}", ex.getMessage());
                completed.set(true);
            }
        };

        emitter.onCompletion(() -> {
            log.info("SSE hoàn thành cho conversation: {}", conversationId);
            completed.set(true);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE timeout cho conversation: {}", conversationId);
            completed.set(true);
            emitter.complete();
        });
        emitter.onError(e -> {
            log.error("SSE gặp lỗi cho conversation {}: {}", conversationId, e.getMessage());
            completed.set(true);
        });

        CompletableFuture.runAsync(() -> {
            try {
                listener.accept(AgentTaskEvent.of(conversationId, AgentEventType.START,
                        "Bắt đầu nhiệm vụ: " + request.message(),
                        Map.of("workspace", request.workspacePath() != null ? request.workspacePath() : "",
                                "subDirectory", request.subDirectory() != null ? request.subDirectory() : "")));

                AgentResponseDto response = runTask(conversationId, request, listener);

                listener.accept(AgentTaskEvent.of(conversationId, AgentEventType.COMPLETE,
                        "Đã hoàn thành toàn bộ nhiệm vụ!",
                        response));

                if (!completed.get()) {
                    emitter.complete();
                }
            } catch (Exception e) {
                log.error("Lỗi ngoại lệ trong luồng SSE Agent: {}", e.getMessage(), e);
                if (!completed.get()) {
                    listener.accept(AgentTaskEvent.of(conversationId, AgentEventType.ERROR,
                            "Lỗi thực thi: " + e.getMessage(),
                            Map.of("error", e.getMessage() != null ? e.getMessage() : "Unknown error")));
                    emitter.completeWithError(e);
                }
            }
        });

        return emitter;
    }

    @Override
    public AgentResponseDto runTests(String projectDir, String testClassName) {
        TestExecutionService.TestResult result = testExecutionService.runMavenTests(projectDir, testClassName);
        String convId = UUID.randomUUID().toString();
        return AgentResponseDto.of(result.toFormattedString(), convId, List.of());
    }

    @Override
    public AgentResponseDto fixAndRetest(String taskDescription, String failureDetails) {
        String prompt = String.format("""
                Yêu cầu ban đầu: %s

                Chi tiết lỗi gặp phải khi kiểm thử / biên dịch:
                %s

                HƯỚNG DẪN THỰC HIỆN:
                1. Đọc kỹ chi tiết lỗi bên trên.
                2. Dùng công cụ readFile hoặc listFiles để kiểm tra tệp tin liên quan trong workspace.
                3. Sửa lại mã nguồn (dùng updateFile hoặc createFile).
                4. Chạy lại kiểm thử tương ứng (runJavaAssertionTest, runMavenTest, hoặc runPythonTest).
                5. Tiếp tục tối ưu và sửa cho đến khi tất cả các kiểm thử đều PASSED.
                6. Báo cáo nguyên nhân lỗi và cách đã giải quyết.
                """, taskDescription, failureDetails);

        return createCodingTask(new AgentRequest(prompt));
    }

    private void emit(Consumer<AgentTaskEvent> listener, AgentTaskEvent event) {
        if (listener != null) {
            try {
                listener.accept(event);
            } catch (Exception e) {
                log.warn("Lỗi khi gửi sự kiện tới SSE listener: {}", e.getMessage());
            }
        }
    }

    private AgentResponseDto runTask(String conversationId, AgentRequest request, Consumer<AgentTaskEvent> eventListener) {
        // Bắt buộc phải có workspace
        if (request.workspacePath() == null || request.workspacePath().isBlank()) {
            throw new IllegalArgumentException("Yêu cầu không hợp lệ, phải có workspacePath");
        }
        try {
            // Kích hoạt workspace từ request người dùng gửi
            fileSystemService.getSecurityService().setWorkspace(request.workspacePath());
            fileSystemService.clearRecentlyModifiedFiles();

            String userPrompt = request.message();
            if (request.subDirectory() != null && !request.subDirectory().isBlank()) {
                userPrompt = "[Thư mục con mục tiêu: " + request.subDirectory() + "]\n" + userPrompt;
            }

            emit(eventListener, AgentTaskEvent.of(conversationId, AgentEventType.ANALYZING,
                    "Đang nạp ngữ cảnh và khảo sát tệp tin liên quan...",
                    Map.of("workspace", request.workspacePath())));

            // 1. Tự động nạp ngữ cảnh file nếu người dùng nhắc đến file cụ thể
            userPrompt = autoEnrichExistingFileContent(userPrompt, request.subDirectory(), eventListener, conversationId);

            // 2. Tự động nạp cấu trúc dự án và quy chuẩn coding style nếu có
            userPrompt = autoEnrichProjectContext(userPrompt, request.subDirectory(), eventListener, conversationId);

            log.info("Agent bắt đầu xử lý task cho [{}] tại thư mục: {}",
                    conversationId, fileSystemService.getSecurityService().getRoot());

            // 3. Vòng lặp Agent ReAct (Multi-step tool calling loop)
            String currentPrompt = userPrompt;
            String finalAnswer = "";
            int maxTurns = 8;

            for (int turn = 0; turn < maxTurns; turn++) {
                emit(eventListener, AgentTaskEvent.of(conversationId, AgentEventType.THINKING,
                        String.format("AI đang suy nghĩ và thực thi bước [%d/%d]...", turn + 1, maxTurns),
                        Map.of("turn", turn + 1, "maxTurns", maxTurns)));

                String answer = codingChatClient.prompt()
                        .advisors(advisor -> advisor.param(ChatMemory.CONVERSATION_ID, conversationId))
                        .user(currentPrompt)
                        .call()
                        .content();

                ToolExecutionResult result = executeToolCall(answer, request.subDirectory(), eventListener, conversationId);
                if (result == null) {
                    // AI đã hoàn thành toàn bộ công việc và đưa ra câu trả lời tổng kết bằng lời
                    finalAnswer = answer;
                    break;
                }

                // Nếu là công cụ ghi file (createFile, updateFile, deleteFile, renameFile):
                if (result.isWriteAction()) {
                    log.info("Agent ReAct loop [turn {}/{}]: Tool '{}' ghi file thành công: {}",
                            turn + 1, maxTurns, result.toolName(), result.output());

                    emit(eventListener, AgentTaskEvent.of(conversationId, AgentEventType.FILE_WRITTEN,
                            "Đã ghi file thành công: " + result.output(),
                            Map.of("tool", result.toolName(), "file", result.output())));

                    // Cung cấp phản hồi tự nhiên cho AI tiếp tục tạo các file còn lại
                    currentPrompt = "File " + result.output() + ". Bạn hãy tiếp tục viết file tiếp theo (Repository, Service, Controller) để hoàn thành đầy đủ tính năng CRUD.";
                    continue;
                }

                // Nếu là công cụ đọc thông tin (getProjectStructure, listFiles, readFile, detectProjectType, searchCode):
                log.info("Agent ReAct loop [turn {}/{}]: Tool '{}' đọc thông tin xong, phản hồi cho AI tiếp tục",
                        turn + 1, maxTurns, result.toolName());

                emit(eventListener, AgentTaskEvent.of(conversationId, AgentEventType.TOOL_RESULT,
                        "Công cụ '" + result.toolName() + "' đã đọc thông tin thành công.",
                        Map.of("tool", result.toolName())));

                currentPrompt = "[KẾT QUẢ TỪ HỆ THỐNG CHO CÔNG CỤ '" + result.toolName() + "']:\n"
                        + result.output()
                        + "\n\n--> BƯỚC TIẾP THEO: Hãy dựa vào thông tin dự án ở trên để tiến hành tạo các file mã nguồn bằng 'createFile'.";
            }

            // Tự động quét và lưu toàn bộ các file mã nguồn có trong câu trả lời của AI (dạng markdown code blocks)
            List<String> autoSaved = autoExtractAndSaveCodeBlocks(finalAnswer, request.subDirectory(), eventListener, conversationId);
            if (!autoSaved.isEmpty()) {
                log.info("Đã tự động trích xuất và tạo {} file mã nguồn từ câu trả lời của AI: {}", autoSaved.size(), autoSaved);
            }

            // 4. Tự động xác minh biên dịch / kiểm thử và tự động sửa lỗi nếu có file mã nguồn Java được tạo hoặc sửa
            List<String> affectedFiles = fileSystemService.getRecentlyModifiedFiles();
            String verificationSummary = "";
            if (!affectedFiles.isEmpty() && affectedFiles.stream().anyMatch(f -> f.endsWith(".java"))) {
                String targetDir = (request.subDirectory() != null && !request.subDirectory().isBlank()) ? request.subDirectory() : ".";
                verificationSummary = verifyAndSelfRepair(targetDir, affectedFiles, eventListener, conversationId);
            }

            if (!affectedFiles.isEmpty()) {
                finalAnswer = "Tôi đã hoàn thành việc tạo và cấu hình đầy đủ các file cho tính năng trong dự án Spring Boot:\n"
                        + String.join("\n", affectedFiles.stream().map(f -> "- " + f).toList())
                        + "\n\nCác file mã nguồn và cấu hình đã được tạo đúng cấu trúc trong thư mục dự án của bạn!"
                        + verificationSummary;
            }

            return AgentResponseDto.of(finalAnswer, conversationId, affectedFiles);

        } finally {
            // Luôn dọn dẹp sau khi request hoàn tất (tránh rò rỉ ThreadLocal)
            fileSystemService.getSecurityService().clearWorkspace();
        }
    }

    private record ToolExecutionResult(String toolName, boolean isWriteAction, String output, String message) {}

    private ToolExecutionResult executeToolCall(String answer, String subDir, Consumer<AgentTaskEvent> eventListener, String conversationId) {
        if (answer == null) return null;

        String trimmed = answer.trim();
        // Kiểm tra xem AI có nhả JSON tool call không
        boolean isTool = trimmed.contains("createFile") || trimmed.contains("updateFile")
                || trimmed.contains("deleteFile") || trimmed.contains("renameFile")
                || trimmed.contains("moveFile") || trimmed.contains("getProjectStructure")
                || trimmed.contains("detectProjectType") || trimmed.contains("readFile")
                || trimmed.contains("listFiles") || trimmed.contains("searchCode")
                || trimmed.contains("generateSpringCrudScaffold") || trimmed.contains("getTemplate")
                || trimmed.contains("getAlgorithmTemplate") || trimmed.contains("planAlgorithm");

        if (!isTool) return null;

        try {
            String toolName = null;
            String rawPath = null;
            String oldPath = null;
            String newPath = null;
            String targetContent = null;
            String replacementContent = null;
            String content = null;
            String sub = null;
            String query = null;
            String basePkg = null;
            String entityName = null;
            String fields = null;
            String templateKey = null;

            // 1. Thử parse bằng Jackson trước
            try {
                String jsonToParse = trimmed;
                int openBraces = countChar(jsonToParse, '{');
                int closeBraces = countChar(jsonToParse, '}');
                if (openBraces > closeBraces) {
                    jsonToParse = jsonToParse + "}".repeat(openBraces - closeBraces);
                }

                ObjectMapper mapper = new ObjectMapper();
                JsonNode root = mapper.readTree(jsonToParse);
                toolName = root.path("name").asText(null);
                JsonNode args = root.path("arguments");
                if (args.isObject()) {
                    rawPath = args.path("path").asText(null);
                    oldPath = args.path("oldPath").asText(null);
                    newPath = args.path("newPath").asText(null);
                    targetContent = args.path("targetContent").asText(null);
                    replacementContent = args.path("replacementContent").asText(null);
                    content = args.path("content").asText(null);
                    sub = args.has("subDir") ? args.path("subDir").asText(null) : args.path("subDirectory").asText(null);
                    query = args.path("query").asText(null);
                    basePkg = args.path("basePackage").asText(null);
                    entityName = args.path("entityName").asText(null);
                    fields = args.path("fields").asText(null);
                    templateKey = args.path("templateKey").asText(null);
                }
            } catch (Exception parseEx) {
                log.warn("Jackson parseTree không thành công: {}. Kích hoạt fallback regex parser.", parseEx.getMessage());
            }

            // 2. Fallback trích xuất bằng regex nếu Jackson không parse được
            if (toolName == null || toolName.isBlank()) toolName = extractFieldFallback(trimmed, "name");
            if (rawPath == null || rawPath.isBlank()) rawPath = extractFieldFallback(trimmed, "path");
            if (oldPath == null || oldPath.isBlank()) oldPath = extractFieldFallback(trimmed, "oldPath");
            if (newPath == null || newPath.isBlank()) newPath = extractFieldFallback(trimmed, "newPath");
            if (targetContent == null) targetContent = extractFieldFallback(trimmed, "targetContent");
            if (replacementContent == null) replacementContent = extractFieldFallback(trimmed, "replacementContent");
            if (content == null) content = extractFieldFallback(trimmed, "content");
            if (sub == null) sub = extractFieldFallback(trimmed, "subDir");
            if (sub == null) sub = extractFieldFallback(trimmed, "subDirectory");
            if (query == null) query = extractFieldFallback(trimmed, "query");
            if (basePkg == null) basePkg = extractFieldFallback(trimmed, "basePackage");
            if (entityName == null) entityName = extractFieldFallback(trimmed, "entityName");
            if (fields == null) fields = extractFieldFallback(trimmed, "fields");
            if (templateKey == null) templateKey = extractFieldFallback(trimmed, "templateKey");

            if (toolName == null) return null;

            emit(eventListener, AgentTaskEvent.of(conversationId, AgentEventType.TOOL_CALL,
                    "Đang thực thi công cụ '" + toolName + "'...",
                    Map.of("tool", toolName, "target", rawPath != null ? rawPath : (sub != null ? sub : ""))));

            // --- A. CÁC CÔNG CỤ SINH CODE & ĐỌC DỮ LIỆU ---
            if ("generateSpringCrudScaffold".equalsIgnoreCase(toolName)) {
                String pkg = (basePkg != null && !basePkg.isBlank()) ? basePkg : (sub != null ? sub : "com.example.demo");
                String ent = (entityName != null && !entityName.isBlank()) ? entityName : "Item";
                String flds = fields != null ? fields : content;
                Map<String, String> scaffold = codeGenerationService.generateSpringCrudScaffold(pkg, ent, flds);
                StringBuilder sb = new StringBuilder();
                sb.append("=== ĐÃ SINH BỘ KHUNG MÃ NGUỒN CRUD CHO '").append(ent).append("' ===\n\n");
                for (Map.Entry<String, String> entry : scaffold.entrySet()) {
                    sb.append("File: `").append(entry.getKey()).append("`\n```java\n").append(entry.getValue()).append("```\n\n");
                }
                sb.append("--> BƯỚC TIẾP THEO: Hãy dùng 'createFile' để tạo các file trên vào thư mục dự án.");
                return new ToolExecutionResult(toolName, false, sb.toString(), null);
            }

            if ("getTemplate".equalsIgnoreCase(toolName) || "getAlgorithmTemplate".equalsIgnoreCase(toolName)) {
                String key = templateKey != null ? templateKey : (query != null ? query : (content != null ? content : "spring-api-response"));
                String res = codeGenerationService.getTemplate(key, basePkg, entityName);
                return new ToolExecutionResult(toolName, false, res, null);
            }

            // --- A. CÁC CÔNG CỤ ĐỌC DỮ LIỆU (READ ACTIONS) ---
            if ("getProjectStructure".equalsIgnoreCase(toolName)) {
                String target = (sub != null && !sub.isBlank()) ? sub : (subDir != null ? subDir : ".");
                String tree = projectAnalysisService.getProjectTree(target, 4);
                return new ToolExecutionResult(toolName, false, tree, null);
            }

            if ("detectProjectType".equalsIgnoreCase(toolName)) {
                String target = (sub != null && !sub.isBlank()) ? sub : (subDir != null ? subDir : ".");
                String type = projectAnalysisService.detectProjectType(target);
                return new ToolExecutionResult(toolName, false, type, null);
            }

            if ("searchCode".equalsIgnoreCase(toolName)) {
                String q = query != null ? query : "";
                String res = projectAnalysisService.searchInFiles(q, null);
                return new ToolExecutionResult(toolName, false, res, null);
            }

            if ("readFile".equalsIgnoreCase(toolName)) {
                String p = rawPath;
                if (subDir != null && !subDir.isBlank() && p != null && !p.startsWith(subDir)) {
                    p = subDir + "/" + p;
                }
                String fileContent = fileSystemService.read(p);
                return new ToolExecutionResult(toolName, false, fileContent, null);
            }

            if ("listFiles".equalsIgnoreCase(toolName)) {
                String target = (sub != null && !sub.isBlank()) ? sub : (subDir != null ? subDir : ".");
                String list = fileSystemService.list(target);
                return new ToolExecutionResult(toolName, false, list, null);
            }

            // --- B. CÁC CÔNG CỤ GHI DỮ LIỆU (WRITE ACTIONS) ---
            // renameFile / moveFile
            if ("renameFile".equalsIgnoreCase(toolName) || "moveFile".equalsIgnoreCase(toolName)) {
                if (oldPath == null && rawPath != null) oldPath = rawPath;
                if (oldPath != null && newPath != null) {
                    String finalOldPath = oldPath;
                    String finalNewPath = newPath;
                    if (subDir != null && !subDir.isBlank()) {
                        if (!finalOldPath.startsWith(subDir)) finalOldPath = subDir + "/" + finalOldPath;
                        if (!finalNewPath.startsWith(subDir)) finalNewPath = subDir + "/" + finalNewPath;
                    }
                    String result = fileSystemService.rename(finalOldPath, finalNewPath);
                    log.info("Tool rename file '{}' -> '{}' : {}", finalOldPath, finalNewPath, result);
                    if (result.startsWith("Lỗi:")) {
                        return new ToolExecutionResult(toolName, true, result, "Không thể đổi tên file: " + result);
                    }
                    return new ToolExecutionResult(toolName, true, result, "Tôi đã đổi tên file từ '" + finalOldPath + "' sang '" + finalNewPath + "' thành công.");
                }
            }

            // Xử lý đường dẫn có kèm thư mục con (ví dụ "student-management/src/...")
            String finalPath = rawPath;
            if (subDir != null && !subDir.isBlank() && finalPath != null && !finalPath.startsWith(subDir)) {
                finalPath = subDir + "/" + finalPath;
            }

            // createFile
            if ("createFile".equalsIgnoreCase(toolName)) {
                String textToCreate = content != null ? content : replacementContent;
                String result = fileSystemService.create(finalPath, textToCreate);
                log.info("Tool create file '{}' -> {}", finalPath, result);
                if (result.startsWith("Lỗi:")) {
                    return new ToolExecutionResult(toolName, true, result, "Không thể tạo file '" + finalPath + "': " + result);
                }
                return new ToolExecutionResult(toolName, true, result, "Tôi đã tạo thành công file '" + finalPath + "'.");
            }

            // updateFile
            if ("updateFile".equalsIgnoreCase(toolName)) {
                String result = fileSystemService.edit(finalPath, targetContent, replacementContent);
                log.info("Tool edit file '{}' -> {}", finalPath, result);
                if (result.startsWith("Lỗi: Không tìm thấy") && isCompleteSourceCode(replacementContent)) {
                    log.warn("updateFile không tìm thấy targetContent nhưng replacementContent là toàn bộ mã nguồn. Tự động chuyển sang ghi đè bằng createFile: {}", finalPath);
                    result = fileSystemService.create(finalPath, replacementContent);
                }
                if (result.startsWith("Lỗi:")) {
                    return new ToolExecutionResult(toolName, true, result, "Không thể cập nhật file '" + finalPath + "': " + result);
                }
                return new ToolExecutionResult(toolName, true, result, "Tôi đã cập nhật thành công file '" + finalPath + "'. Nội dung đã được thay đổi theo yêu cầu.");
            }

            // deleteFile
            if ("deleteFile".equalsIgnoreCase(toolName)) {
                String result = fileSystemService.delete(finalPath);
                log.info("Tool delete file '{}' -> {}", finalPath, result);
                if (result.startsWith("Lỗi:")) {
                    return new ToolExecutionResult(toolName, true, result, "Không thể xóa file '" + finalPath + "': " + result);
                }
                return new ToolExecutionResult(toolName, true, result, "Tôi đã xóa thành công file '" + finalPath + "'.");
            }

        } catch (Exception e) {
            log.warn("Lỗi khi xử lý executeToolCall: {}. Raw: {}", e.getMessage(), answer);
        }

        return null;
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
        return sb.length() > 0 ? sb.toString().trim() : (details.length() > 600 ? details.substring(0, 600) + "..." : details.trim());
    }

    private boolean isCompleteSourceCode(String content) {
        if (content == null) return false;
        String trimmed = content.trim();
        return trimmed.contains("class ")
                || trimmed.startsWith("import ")
                || trimmed.startsWith("package ")
                || trimmed.contains("def ")
                || trimmed.contains("public static void main");
    }

    private String extractFieldFallback(String rawJson, String fieldName) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"" + java.util.regex.Pattern.quote(fieldName) + "\"\\s*:\\s*\"");
        java.util.regex.Matcher m = p.matcher(rawJson);
        if (!m.find()) return null;
        int start = m.end();
        StringBuilder val = new StringBuilder();
        boolean escaped = false;
        for (int i = start; i < rawJson.length(); i++) {
            char c = rawJson.charAt(i);
            if (escaped) {
                if (c == 'n') val.append('\n');
                else if (c == 't') val.append('\t');
                else if (c == 'r') val.append('\r');
                else if (c == '"') val.append('"');
                else if (c == '\\') val.append('\\');
                else val.append(c);
                escaped = false;
            } else if (c == '\\') {
                escaped = true;
            } else if (c == '"') {
                String remainder = rawJson.substring(i + 1).trim();
                if (remainder.startsWith(",") || remainder.startsWith("}")) {
                    return val.toString();
                } else {
                    val.append('"');
                }
            } else {
                val.append(c);
            }
        }
        return val.toString();
    }

    private int countChar(String str, char ch) {
        int count = 0;
        for (char c : str.toCharArray()) {
            if (c == ch) count++;
        }
        return count;
    }

    private String autoEnrichExistingFileContent(String prompt, String subDir, Consumer<AgentTaskEvent> eventListener, String conversationId) {
        try {
            Matcher m = Pattern.compile("([a-zA-Z0-9_.-]+\\.(java|py|txt|json|xml|md|html|css|js|ts|cpp|c|h|cs))").matcher(prompt);
            StringBuilder sb = new StringBuilder(prompt);
            java.util.Set<String> processedFiles = new java.util.HashSet<>();

            while (m.find()) {
                String fileName = m.group(1);
                String targetPath = null;
                if (subDir != null && !subDir.isBlank() && fileSystemService.exists(subDir + "/" + fileName)) {
                    targetPath = subDir + "/" + fileName;
                } else if (fileSystemService.exists(fileName)) {
                    targetPath = fileName;
                }

                if (targetPath != null && processedFiles.add(targetPath)) {
                    String existingContent = fileSystemService.read(targetPath);
                    sb.append("\n\n--- THÔNG TIN NỘI DUNG HIỆN TẠI TỪ WORKSPACE ---\n")
                            .append("Nội dung file '").append(targetPath).append("' trên đĩa:\n")
                            .append("```\n")
                            .append(existingContent)
                            .append("\n```\n")
                            .append("HƯỚNG DẪN BẮT BUỘC:\n")
                            .append("1. Khi đổi tên file (rename): BẮT BUỘC DÙNG 'renameFile' với oldPath và newPath. Tuyệt đối không dùng 'createFile' vì sẽ làm thừa file cũ.\n")
                            .append("2. Khi thêm thuật toán mới hoặc viết mã nguồn hoàn chỉnh: BẮT BUỘC DÙNG 'createFile' với đường dẫn '").append(targetPath).append("' và toàn bộ mã nguồn hoàn chỉnh mới để ghi đè toàn bộ file.\n")
                            .append("3. TUYỆT ĐỐI KHÔNG dùng 'updateFile' để thay thế một dòng lệnh bằng cả một class hoàn chỉnh (sẽ làm lồng class trong class gây lỗi biên dịch).\n")
                            .append("4. Chỉ dùng 'updateFile' khi sửa một vài dòng cục bộ, không chứa khai báo class hay import.\n")
                            .append("--------------------------------------------------\n");
                    log.info("Đã tự động nạp nội dung file '{}' vào prompt cho Agent", targetPath);

                    emit(eventListener, AgentTaskEvent.of(conversationId, AgentEventType.ANALYZING,
                            "Đã nạp nội dung tệp tin '" + targetPath + "' vào ngữ cảnh.",
                            Map.of("file", targetPath)));
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.warn("Lỗi autoEnrichExistingFileContent: {}", e.getMessage());
            return prompt;
        }
    }

    private String detectAndReadCodingStyle(String targetDir) {
        List<String> candidatePaths = List.of(
                targetDir + "/CODING_STYLE.md",
                targetDir + "/coding_style.md",
                targetDir + "/CONVENTIONS.md",
                targetDir + "/conventions.md",
                targetDir + "/.cursorrules",
                "CODING_STYLE.md",
                "CONVENTIONS.md",
                ".cursorrules"
        );

        for (String path : candidatePaths) {
            try {
                if (fileSystemService.exists(path)) {
                    String content = fileSystemService.read(path);
                    if (content != null && !content.isBlank()) {
                        log.info("Đã phát hiện file quy chuẩn phong cách lập trình tại: {}", path);
                        return content.trim();
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    private String autoEnrichProjectContext(String prompt, String subDir, Consumer<AgentTaskEvent> eventListener, String conversationId) {
        try {
            String targetDir = (subDir != null && !subDir.isBlank()) ? subDir : ".";
            if (fileSystemService.exists(targetDir)) {
                emit(eventListener, AgentTaskEvent.of(conversationId, AgentEventType.ANALYZING,
                        "Đang khảo sát cấu trúc dự án tại: " + targetDir,
                        Map.of("targetDir", targetDir)));

                StringBuilder sb = new StringBuilder(prompt);

                // 0. Quét file CODING_STYLE.md hoặc CONVENTIONS.md nếu có trong workspace
                String codingStyle = detectAndReadCodingStyle(targetDir);
                if (codingStyle != null) {
                    sb.append("\n\n--- QUY CHUẨN PHONG CÁCH LẬP TRÌNH CỦA DỰ ÁN (CODING STYLE & CONVENTIONS) ---\n")
                            .append(codingStyle).append("\n")
                            .append("--> HƯỚNG DẪN BẮT BUỘC: Bạn PHẢI tuân thủ 100% các quy chuẩn trên khi tạo hoặc sửa mã nguồn.\n")
                            .append("---------------------------------------------------------------------------\n");

                    emit(eventListener, AgentTaskEvent.of(conversationId, AgentEventType.ANALYZING,
                            "Đã phát hiện và nạp file quy chuẩn phong cách lập trình (CODING_STYLE.md) vào ngữ cảnh của Agent."));
                }

                // Ưu tiên 1: Tự động phân tích dự án Spring Boot (Spring Initializr) có sẵn
                String springBootInfo = projectAnalysisService.analyzeSpringBootProject(targetDir);
                if (springBootInfo != null) {
                    sb.append(springBootInfo);
                    log.info("Đã phát hiện dự án Spring Boot tại '{}', tự động nạp cấu trúc và package gốc vào prompt cho Agent", targetDir);

                    emit(eventListener, AgentTaskEvent.of(conversationId, AgentEventType.ANALYZING,
                            "Đã phát hiện dự án Spring Boot, tự động nạp cấu trúc package và dependencies.",
                            Map.of("type", "SpringBoot", "targetDir", targetDir)));

                    return sb.toString();
                }

                // Ưu tiên 2: Cây thư mục dự án thông thường
                String tree = projectAnalysisService.getProjectTree(targetDir, 6);
                String projectType = projectAnalysisService.detectProjectType(targetDir);

                sb.append("\n\n--- THÔNG TIN CẤU TRÚC DỰ ÁN HIỆN TẠI TỪ WORKSPACE ---\n")
                        .append("Thư mục khảo sát: ").append(targetDir).append("\n")
                        .append(projectType).append("\n")
                        .append(tree).append("\n")
                        .append("------------------------------------------------------\n")
                        .append("HƯỚNG DẪN QUAN TRỌNG CHO AGENT:\n")
                        .append("1. Bạn ĐÃ CÓ sẵn cấu trúc thư mục và các file hiện có ở trên. TUYỆT ĐỐI KHÔNG gọi lại getProjectStructure hay listFiles.\n")
                        .append("2. Hãy dựa vào cấu trúc package và các thư mục có sẵn để tạo đầy đủ các file mã nguồn (Entity/Model, Repository, Service, Controller) bằng công cụ 'createFile'.\n")
                        .append("------------------------------------------------------\n");
                log.info("Đã tự động nạp cấu trúc dự án '{}' vào prompt cho Agent", targetDir);
                return sb.toString();
            }
        } catch (Exception e) {
            log.warn("Lỗi autoEnrichProjectContext: {}", e.getMessage());
        }
        return prompt;
    }

    private List<String> autoExtractAndSaveCodeBlocks(String answer, String subDir, Consumer<AgentTaskEvent> eventListener, String conversationId) {
        List<String> createdFiles = new ArrayList<>();
        if (answer == null || !answer.contains("```")) {
            return createdFiles;
        }

        String textToParse = answer;
        // Bù dấu ``` nếu khối code cuối cùng bị cắt cụt do chạm giới hạn token
        int countBackticks = 0;
        int idx = 0;
        while ((idx = textToParse.indexOf("```", idx)) != -1) {
            countBackticks++;
            idx += 3;
        }
        if (countBackticks % 2 != 0) {
            textToParse = textToParse + "\n```";
        }

        // Hỗ trợ mọi loại thẻ ngôn ngữ (java, properties, yaml, yml, xml, sql, json, v.v.)
        Pattern codeBlockPattern = Pattern.compile("```(?:[a-zA-Z0-9_.-]+)?\\s*\\n([\\s\\S]*?)\\n```");
        Matcher matcher = codeBlockPattern.matcher(textToParse);

        int lastEnd = 0;
        while (matcher.find()) {
            String codeContent = matcher.group(1).trim();
            int blockStart = matcher.start();
            String precedingText = answer.substring(lastEnd, blockStart);
            lastEnd = matcher.end();

            if (codeContent.startsWith("{") && codeContent.endsWith("}")) continue;
            if (codeContent.length() < 10) continue;

            String filePath = null;

            // 1. Thử tìm đường dẫn được ghi tường minh trong đoạn văn trước code block
            Matcher pathMatcher = Pattern.compile("`([a-zA-Z0-9_./\\\\-]+\\.(java|py|xml|properties|yml|yaml|txt|md|sql)|Dockerfile|docker-compose\\.yml|docker-compose\\.yaml)`").matcher(precedingText);
            String lastFoundPath = null;
            while (pathMatcher.find()) {
                lastFoundPath = pathMatcher.group(1);
            }
            if (lastFoundPath != null) {
                filePath = lastFoundPath;
            }

            // 2. Nếu không có đường dẫn tường minh, tự suy luận từ nội dung
            if (filePath == null) {
                if (codeContent.contains("class ") || codeContent.contains("interface ") || codeContent.contains("enum ") || codeContent.contains("record ")) {
                    Matcher pkgMatcher = Pattern.compile("package\\s+([a-zA-Z0-9_.]+);").matcher(codeContent);
                    Matcher clsMatcher = Pattern.compile("(?:public\\s+)?(?:class|interface|enum|record)\\s+([a-zA-Z0-9_]+)").matcher(codeContent);
                    if (pkgMatcher.find() && clsMatcher.find()) {
                        String pkg = pkgMatcher.group(1);
                        String cls = clsMatcher.group(1);
                        boolean isTest = cls.endsWith("Test") || codeContent.contains("@Test");
                        String rootSrc = isTest ? "src/test/java/" : "src/main/java/";
                        filePath = rootSrc + pkg.replace('.', '/') + "/" + cls + ".java";
                    }
                } else if (codeContent.contains("spring.datasource") || codeContent.contains("spring.jpa")) {
                    filePath = "src/main/resources/application.properties";
                } else if (codeContent.contains("spring:") && (codeContent.contains("datasource:") || codeContent.contains("jpa:"))) {
                    filePath = "src/main/resources/application.yaml";
                } else if (codeContent.contains("<project") && codeContent.contains("<modelVersion>")) {
                    filePath = "pom.xml";
                } else if (codeContent.contains("FROM ") && codeContent.contains("EXPOSE ")) {
                    filePath = "Dockerfile";
                } else if ((codeContent.contains("services:") || codeContent.contains("version:")) && codeContent.contains("image:")) {
                    filePath = "docker-compose.yml";
                }
            }

            // 3. Chuẩn hóa đường dẫn: nếu là file cấu hình mà chưa có src/main/resources thì thêm vào
            if (filePath != null) {
                if ((filePath.endsWith("application.properties") || filePath.endsWith("application.yaml") || filePath.endsWith("application.yml"))
                        && !filePath.contains("src/main/resources")) {
                    filePath = "src/main/resources/" + filePath;
                }
                String finalPath = filePath.replace('\\', '/');
                while (finalPath.startsWith("/")) {
                    finalPath = finalPath.substring(1);
                }
                if (subDir != null && !subDir.isBlank() && !finalPath.startsWith(subDir)) {
                    finalPath = subDir + "/" + finalPath;
                }
                String result = fileSystemService.create(finalPath, codeContent);
                log.info("Auto-saved code block to file '{}': {}", finalPath, result);
                createdFiles.add(finalPath);

                emit(eventListener, AgentTaskEvent.of(conversationId, AgentEventType.FILE_WRITTEN,
                        "Đã trích xuất và tạo file: " + finalPath,
                        Map.of("file", finalPath, "size", codeContent.length())));
            }
        }
        return createdFiles;
    }

    private String verifyAndSelfRepair(String targetDir, List<String> affectedFiles, Consumer<AgentTaskEvent> eventListener, String conversationId) {
        if (!fileSystemService.exists(targetDir + "/pom.xml") && !fileSystemService.exists("pom.xml")) {
            return "";
        }

        emit(eventListener, AgentTaskEvent.of(conversationId, AgentEventType.THINKING,
                "Đang tự động xác minh biên dịch mã nguồn vừa tạo bằng Maven..."));

        TestExecutionService.TestResult compileResult = testExecutionService.runMavenCompile(targetDir);
        if (compileResult.passed()) {
            emit(eventListener, AgentTaskEvent.of(conversationId, AgentEventType.TOOL_RESULT,
                    "Xác minh hoàn tất: Toàn bộ mã nguồn đã BIÊN DỊCH THÀNH CÔNG (BUILD SUCCESS)."));
            log.info("Xác minh biên dịch thành công cho '{}'", targetDir);
            return "\n\n[XÁC MINH TỰ ĐỘNG]: Toàn bộ mã nguồn đã BIÊN DỊCH THÀNH CÔNG (BUILD SUCCESS) qua Maven!";
        }

        // Biên dịch thất bại: Bắt đầu chu trình tự động sửa lỗi
        log.warn("Lỗi biên dịch Maven tại '{}'. Bắt đầu tự động khắc phục...", targetDir);
        List<String> repairedFiles = new ArrayList<>();

        // Bước 1: Quick-Fix tự động cho lỗi Spring Boot 3 (javax.persistence -> jakarta) & thiếu import
        String rawErrors = compileResult.details();
        boolean didQuickFix = performAutomatedQuickFixes(targetDir, rawErrors, repairedFiles);
        if (didQuickFix) {
            log.info("Đã áp dụng Quick-Fix tự động cho: {}. Thử biên dịch lại qua Maven...", repairedFiles);
            compileResult = testExecutionService.runMavenCompile(targetDir);
            if (compileResult.passed()) {
                for (String rf : repairedFiles) {
                    if (!affectedFiles.contains(rf)) affectedFiles.add(rf);
                }
                emit(eventListener, AgentTaskEvent.of(conversationId, AgentEventType.TOOL_RESULT,
                        "Hệ thống đã tự động sửa các lỗi cú pháp/package và biên dịch THÀNH CÔNG!",
                        Map.of("repairedFiles", repairedFiles)));

                try {
                    ExperienceRecord exp = new ExperienceRecord(
                            UUID.randomUUID().toString(),
                            "SPRING_BOOT_3_MIGRATION_ERROR",
                            "Lỗi javax.persistence hoặc thiếu import Spring stereotype annotations",
                            extractMavenErrors(rawErrors),
                            "Đã tự động chuyển javax.persistence sang jakarta.persistence và bổ sung import (@Service, @Repository, @RestController)",
                            String.join("\n", repairedFiles),
                            targetDir,
                            "Tự động sửa lỗi package và import Spring Boot 3",
                            List.of("maven", "spring-boot-3", "jakarta", "auto-quick-fix")
                    );
                    agentExperienceService.recordExperience(exp);
                } catch (Exception e) {
                    log.warn("Không thể lưu bài học vào Episodic Memory: {}", e.getMessage());
                }

                return "\n\n[TỰ ĐỘNG SỬA LỖI THÀNH CÔNG]: Hệ thống đã tự động phát hiện và khắc phục các lỗi biên dịch:\n"
                        + String.join("\n", repairedFiles.stream().map(f -> "- Đã sửa: " + f).toList())
                        + "\nToàn bộ dự án đã BIÊN DỊCH THÀNH CÔNG (BUILD SUCCESS) qua Maven!";
            }
        }

        // Bước 2: Tự sửa lỗi qua LLM kết hợp Episodic Memory (pgvector)
        int maxRetries = 2;
        int currentRetry = 0;
        while (!compileResult.passed() && currentRetry < maxRetries) {
            currentRetry++;
            String cleanErrors = extractMavenErrors(compileResult.details());
            log.warn("Tự sửa lỗi qua LLM (lần {}/{}):\n{}", currentRetry, maxRetries, cleanErrors);

            emit(eventListener, AgentTaskEvent.of(conversationId, AgentEventType.THINKING,
                    String.format("Đang phân tích lỗi biên dịch và truy hồi kinh nghiệm từ pgvector (lần %d/%d)...", currentRetry, maxRetries),
                    Map.of("errors", cleanErrors)));

            List<ExperienceRecord> lessons = List.of();
            try {
                lessons = agentExperienceService.findSimilarExperiences(cleanErrors, 2, 0.6);
            } catch (Exception ex) {
                log.warn("Lỗi khi tìm kiếm bài học trong Episodic Memory: {}", ex.getMessage());
            }

            String lessonsPrompt = lessons.isEmpty() ? "" : "\n" + agentExperienceService.formatForPrompt(lessons);

            String repairPrompt = String.format("""
                    Dự án gặp lỗi biên dịch Maven sau khi sinh mã nguồn.
                    
                    DANH SÁCH LỖI BIÊN DỊCH MAVEN:
                    %s
                    %s
                    
                    HƯỚNG DẪN SỬA LỖI BẮT BUỘC:
                    1. Đọc kỹ dòng lỗi và tên file bị lỗi ở trên.
                    2. Nếu lỗi 'package javax.persistence does not exist': BẮT BUỘC đổi toàn bộ sang 'jakarta.persistence.*'.
                    3. Nếu lỗi 'cannot find symbol: class Service': BẮT BUỘC thêm 'import org.springframework.stereotype.Service;'.
                    4. Nếu lỗi 'cannot find symbol: class Repository': BẮT BUỘC thêm 'import org.springframework.stereotype.Repository;'.
                    5. Hãy viết lại mã nguồn hoàn chỉnh của các file bị lỗi. Bọc mỗi file trong dấu backtick (ví dụ: `đường_dẫn_file.java`) kèm khối code markdown ```java ... ```.
                    """, cleanErrors, lessonsPrompt);

            String repairAnswer = codingChatClient.prompt()
                    .user(repairPrompt)
                    .call()
                    .content();

            List<String> newSaved = autoExtractAndSaveCodeBlocks(repairAnswer, targetDir, eventListener, conversationId);
            for (String ns : newSaved) {
                if (!affectedFiles.contains(ns)) affectedFiles.add(ns);
                if (!repairedFiles.contains(ns)) repairedFiles.add(ns);
            }

            compileResult = testExecutionService.runMavenCompile(targetDir);
            if (compileResult.passed()) {
                log.info("Self-repair qua LLM thành công ở lần {}", currentRetry);
                try {
                    ExperienceRecord exp = new ExperienceRecord(
                            UUID.randomUUID().toString(),
                            "MAVEN_COMPILE_ERROR",
                            cleanErrors.length() > 120 ? cleanErrors.substring(0, 120).trim() : cleanErrors.trim(),
                            cleanErrors,
                            "AI Agent đã tự động sửa lỗi biên dịch thành công qua Maven",
                            repairAnswer.length() > 1000 ? repairAnswer.substring(0, 1000) : repairAnswer,
                            targetDir,
                            "Tự động sửa lỗi biên dịch dự án Maven",
                            List.of("maven", "compile-repair", "spring-boot-3")
                    );
                    agentExperienceService.recordExperience(exp);
                } catch (Exception ex) {
                    log.warn("Không thể lưu bài học sau self-repair: {}", ex.getMessage());
                }

                emit(eventListener, AgentTaskEvent.of(conversationId, AgentEventType.TOOL_RESULT,
                        "Self-repair thành công: Mã nguồn đã BIÊN DỊCH THÀNH CÔNG (BUILD SUCCESS) qua Maven!",
                        Map.of("repairedFiles", newSaved)));

                return "\n\n[TỰ ĐỘNG SỬA LỖI THÀNH CÔNG]: AI Agent đã tự động sửa lỗi và biên dịch thành công (BUILD SUCCESS) qua Maven!\n"
                        + String.join("\n", repairedFiles.stream().map(f -> "- Đã sửa: " + f).toList());
            }
        }

        // Nếu vẫn không biên dịch được sau các bước
        String finalCleanErrors = extractMavenErrors(compileResult.details());
        emit(eventListener, AgentTaskEvent.of(conversationId, AgentEventType.TOOL_RESULT,
                "Cảnh báo: Vẫn còn lỗi biên dịch sau khi thử tự sửa.",
                Map.of("details", finalCleanErrors)));
        return "\n\n[CẢNH BÁO BIÊN DỊCH]: Có lỗi khi biên dịch bằng Maven:\n" + finalCleanErrors;
    }

    private boolean performAutomatedQuickFixes(String targetDir, String errorDetails, List<String> repairedFiles) {
        boolean modifiedAny = false;
        try {
            java.nio.file.Path rootPath = fileSystemService.getSecurityService().getRoot();
            java.nio.file.Path projectPath = (targetDir != null && !targetDir.isBlank() && !".".equals(targetDir))
                    ? rootPath.resolve(targetDir) : rootPath;

            if (!java.nio.file.Files.exists(projectPath)) return false;

            try (java.util.stream.Stream<java.nio.file.Path> stream = java.nio.file.Files.walk(projectPath, 12)) {
                List<java.nio.file.Path> javaFiles = stream
                        .filter(java.nio.file.Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !p.toString().contains("target") && !p.toString().contains(".git"))
                        .toList();

                for (java.nio.file.Path jf : javaFiles) {
                    String content = java.nio.file.Files.readString(jf, java.nio.charset.StandardCharsets.UTF_8);
                    String updated = content;
                    boolean changed = false;

                    // 1. Sửa lỗi javax.persistence -> jakarta.persistence
                    if (updated.contains("javax.persistence.")) {
                        updated = updated.replace("javax.persistence.", "jakarta.persistence.");
                        changed = true;
                    }

                    // 2. Sửa lỗi thiếu import @Service
                    if (updated.contains("@Service") && !updated.contains("org.springframework.stereotype.Service")) {
                        updated = addImportAfterPackage(updated, "import org.springframework.stereotype.Service;");
                        changed = true;
                    }

                    // 3. Sửa lỗi thiếu import @Repository
                    if (updated.contains("@Repository") && !updated.contains("org.springframework.stereotype.Repository")) {
                        updated = addImportAfterPackage(updated, "import org.springframework.stereotype.Repository;");
                        changed = true;
                    }

                    // 4. Sửa lỗi thiếu import @RestController hoặc @Controller
                    if ((updated.contains("@RestController") || updated.contains("@Controller"))
                            && !updated.contains("org.springframework.web.bind.annotation")) {
                        updated = addImportAfterPackage(updated, "import org.springframework.web.bind.annotation.*;");
                        changed = true;
                    }

                    if (changed) {
                        java.nio.file.Files.writeString(jf, updated, java.nio.charset.StandardCharsets.UTF_8);
                        String relPath = rootPath.relativize(jf).toString().replace('\\', '/');
                        repairedFiles.add(relPath);
                        modifiedAny = true;
                        log.info("Automated Quick-Fix đã khắc phục thành công file: {}", relPath);
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Lỗi trong quá trình thực hiện Automated Quick-Fix: {}", ex.getMessage());
        }
        return modifiedAny;
    }

    private String addImportAfterPackage(String source, String importStatement) {
        if (source.contains(importStatement.trim())) return source;
        Matcher m = Pattern.compile("(?m)^package\\s+[^;]+;\\s*").matcher(source);
        if (m.find()) {
            return source.substring(0, m.end()) + "\n" + importStatement + "\n" + source.substring(m.end());
        }
        return importStatement + "\n" + source;
    }
}
