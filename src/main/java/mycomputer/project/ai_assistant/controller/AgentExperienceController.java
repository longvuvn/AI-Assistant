package mycomputer.project.ai_assistant.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import mycomputer.project.ai_assistant.dto.response.ApiResponse;
import mycomputer.project.ai_assistant.memory.episodic.model.ExperienceRecord;
import mycomputer.project.ai_assistant.memory.episodic.service.AgentExperienceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/agent/experience")
@RequiredArgsConstructor
@Tag(name = "2. Episodic Memory (pgvector)", description = "Lưu trữ, tra cứu và tự động học hỏi từ các ca sửa lỗi thành công của Agent")
public class AgentExperienceController {

    private final AgentExperienceService agentExperienceService;

    /**
     * Tìm kiếm các kinh nghiệm sửa lỗi hoặc bài học tương tự bằng Semantic Search (pgvector)
     */
    @Operation(summary = "Tìm kiếm bài học kinh nghiệm tương tự bằng Semantic Search (HNSW Cosine)")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<ExperienceRecord>>> searchExperience(
            @RequestParam String query,
            @RequestParam(defaultValue = "3") int topK,
            @RequestParam(defaultValue = "0.6") double threshold) {

        List<ExperienceRecord> results = agentExperienceService.findSimilarExperiences(query, topK, threshold);
        return ResponseEntity.ok(ApiResponse.success(results));
    }

    /**
     * Lấy danh sách các kinh nghiệm sửa lỗi được tích lũy gần đây nhất
     */
    @Operation(summary = "Lấy danh sách các bài học kinh nghiệm mới tích lũy gần đây")
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<ExperienceRecord>>> getRecentExperiences(
            @RequestParam(defaultValue = "10") int limit) {

        List<ExperienceRecord> recent = agentExperienceService.getRecentExperiences(limit);
        return ResponseEntity.ok(ApiResponse.success(recent));
    }

    /**
     * Lưu thủ công một kinh nghiệm hoặc đoạn code mẫu chuẩn vàng (Golden Standard)
     */
    @Operation(summary = "Lưu thủ công một bài học kinh nghiệm hoặc code mẫu chuẩn vàng")
    @PostMapping
    public ResponseEntity<ApiResponse<ExperienceRecord>> saveExperience(@RequestBody ExperienceRecord record) {
        ExperienceRecord saved = agentExperienceService.recordExperience(record);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Đã lưu kinh nghiệm thành công", saved));
    }

    /**
     * Xóa một kinh nghiệm theo ID
     */
    @Operation(summary = "Xóa một bài học kinh nghiệm theo ID")
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteExperience(@PathVariable String id) {
        agentExperienceService.deleteExperience(id);
        return ResponseEntity.ok(ApiResponse.success("Đã xóa kinh nghiệm thành công", null));
    }

    /**
     * Đếm tổng số kinh nghiệm đã lưu trong pgvector
     */
    @Operation(summary = "Đếm tổng số bài học kinh nghiệm đã tích lũy trong pgvector")
    @GetMapping("/count")
    public ResponseEntity<ApiResponse<Long>> countExperiences() {
        long count = agentExperienceService.countExperiences();
        return ResponseEntity.ok(ApiResponse.success(count));
    }
}
