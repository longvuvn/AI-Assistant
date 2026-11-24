package mycomputer.project.ai_assistant.memory.episodic.service;

import mycomputer.project.ai_assistant.memory.episodic.model.ExperienceRecord;

import java.util.List;

public interface AgentExperienceService {

    /**
     * Tự động lưu một trường hợp sửa lỗi hoặc giải pháp kỹ thuật thành công vào pgvector
     */
    ExperienceRecord recordExperience(ExperienceRecord experience);

    /**
     * Tìm kiếm các kinh nghiệm đã tích lũy trong quá khứ có độ tương đồng ngữ nghĩa cao nhất
     *
     * @param query Thông điệp lỗi hoặc đoạn log cần tìm giải pháp
     * @param topK Số lượng kết quả tối đa
     * @param threshold Ngưỡng tương đồng cosine (0.0 đến 1.0)
     * @return Danh sách các kinh nghiệm tương tự kèm điểm số similarityScore
     */
    List<ExperienceRecord> findSimilarExperiences(String query, int topK, double threshold);

    /**
     * Lấy danh sách các kinh nghiệm sửa lỗi được lưu gần đây nhất
     */
    List<ExperienceRecord> getRecentExperiences(int limit);

    /**
     * Xóa một kinh nghiệm theo ID
     */
    void deleteExperience(String experienceId);

    /**
     * Đếm tổng số lượng kinh nghiệm đã lưu trong cơ sở dữ liệu
     */
    long countExperiences();

    /**
     * Định dạng danh sách kinh nghiệm thành khối văn bản Markdown sẵn sàng chèn vào Prompt cho Agent
     */
    String formatForPrompt(List<ExperienceRecord> experiences);
}
