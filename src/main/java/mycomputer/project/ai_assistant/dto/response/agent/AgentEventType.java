package mycomputer.project.ai_assistant.dto.response.agent;

public enum AgentEventType {
    START,          // Khởi tạo tiến trình thực thi nhiệm vụ
    ANALYZING,      // Phân tích cấu trúc thư mục / nạp ngữ cảnh dự án
    THINKING,       // Agent đang suy nghĩ / lập kế hoạch (ReAct loop turn)
    TOOL_CALL,      // Agent gọi công cụ (createFile, updateFile, getProjectStructure...)
    TOOL_RESULT,    // Kết quả trả về từ công cụ
    FILE_WRITTEN,   // Ghi / cập nhật file thành công vào đĩa
    COMPLETE,       // Hoàn tất toàn bộ nhiệm vụ
    ERROR           // Lỗi xảy ra trong quá trình thực thi
}
