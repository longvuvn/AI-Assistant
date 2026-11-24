package mycomputer.project.ai_assistant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Assistant & Multi-Agent SDLC Platform")
                        .version("1.0.0")
                        .description("""
                                ## Nền tảng Trợ Lý Lập Trình & Hệ Thống Đa Tác Tử (Multi-Agent System)
                                
                                Hệ thống cung cấp các phân hệ API:
                                - **1. Multi-Agent SDLC Orchestrator**: Điều phối 7 AI Agent (BA, Architect, API Designer, Developer, Tester, Security, DevOps) theo chu trình phần mềm chuẩn.
                                - **2. Episodic Memory (pgvector)**: Tự động lưu trữ và truy hồi bài học kinh nghiệm sửa lỗi qua vector embeddings (bge-m3).
                                - **3. AI Coding Agent & ReAct Tool Calling**: Khảo sát workspace, sinh mã nguồn Clean Architecture, tự động biên dịch và kiểm thử qua Maven.
                                - **4. RAG Knowledge Base**: Đọc tài liệu PDF, phân mảnh và tìm kiếm tương đồng qua pgvector.
                                - **5. Chat Memory Assistant**: Hội thoại bền vững qua Spring AI Chat Memory (JDBC PostgreSQL).
                                """)
                        .contact(new Contact()
                                .name("Đồ Án Thạc Sĩ Công Nghệ Thông Tin")
                                .email("admin@ai-assistant.local"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8082").description("Môi trường Local Development"),
                        new Server().url("http://localhost:8080").description("Môi trường Docker Production")
                ));
    }
}
