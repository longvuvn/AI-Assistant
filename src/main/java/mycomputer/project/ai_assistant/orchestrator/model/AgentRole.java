package mycomputer.project.ai_assistant.orchestrator.model;

public enum AgentRole {
    BA("Business Analyst", "Phân tích yêu cầu & tạo PRD"),
    ARCHITECT("Solution Architect", "Thiết kế kiến trúc & sơ đồ Mermaid"),
    API_DESIGNER("API Designer", "Thiết kế hợp đồng RESTful API & DTOs"),
    DEVELOPER("Software Developer", "Lập trình mã nguồn Spring Boot"),
    TESTER("QA & Tester", "Viết & thực thi kiểm thử tự động"),
    SECURITY("Security Auditor", "Thẩm định an toàn mã nguồn OWASP"),
    DEVOPS("DevOps Engineer", "Đóng gói Docker & cấu hình Production");

    private final String displayName;
    private final String description;

    AgentRole(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
