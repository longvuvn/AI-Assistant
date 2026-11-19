# AI Assistant

Trợ lý AI hỗ trợ học tập dành cho sinh viên thạc sĩ ngành Công nghệ thông tin. Xây dựng bằng **Spring Boot 4** + **Spring AI** + **Ollama**, chạy hoàn toàn offline trên máy cá nhân.

## Tính năng chính

- **Chat thông minh** -- hỏi đáp, giải thích khái niệm, hỗ trợ streaming (SSE) và lưu ngữ cảnh hội thoại.
- **RAG (Retrieval-Augmented Generation)** -- upload tài liệu PDF/Markdown, lưu vector vào PGVector, truy vấn và trả lời dựa trên nội dung tài liệu.
- **AI Coding Agent** -- nhận yêu cầu bài toán, tự sinh mã nguồn, viết unit test, chạy test, và tự sửa lỗi cho đến khi pass. Hỗ trợ Java, Python, C++.

## Công nghệ

| Thành phần | Chi tiết |
|---|---|
| Framework | Spring Boot 4.0.9, Spring AI 2.0.1 |
| LLM | Ollama (chat: `qwen2.5:3b`, coding: `qwen2.5-coder:7b`) |
| Embedding | `bge-m3` (1024 dimensions) |
| Vector Store | PostgreSQL + PGVector (HNSW, Cosine Distance) |
| Ngôn ngữ | Java 21 |
| Build | Maven |

## Yêu cầu

- Java 21+
- Docker (để chạy PostgreSQL + PGVector)
- Ollama đã cài và pull sẵn các model: `qwen2.5:3b`, `qwen2.5-coder:7b`, `bge-m3`

## Cài đặt và chạy

### 1. Khởi động database

```bash
docker compose up -d
```

Lệnh này sẽ chạy PostgreSQL với PGVector trên port **5433**.

### 2. Cài model Ollama

```bash
ollama pull qwen2.5:3b
ollama pull qwen2.5-coder:7b
ollama pull bge-m3
```

### 3. Chạy ứng dụng

```bash
./mvnw spring-boot:run
```

Ứng dụng chạy tại `http://localhost:8082`.

## API Endpoints

### Chat (`/api/v1/chat`)

| Method | Path | Mô tả |
|---|---|---|
| POST | `/ask` | Hỏi đáp đơn giản |
| GET | `/stream?message=...` | Streaming response (SSE) |
| POST | `/conversation` | Hỏi đáp có nhớ ngữ cảnh (cần `conversationId`) |
| POST | `/explain` | Giải thích chủ đề cho người mới |

### RAG (`/api/v1/rag`)

| Method | Path | Mô tả |
|---|---|---|
| POST | `/upload` | Upload file PDF để lưu vào vector store |
| POST | `/search` | Tìm kiếm nội dung tương đồng trong tài liệu |
| POST | `/ask` | Hỏi đáp dựa trên tài liệu đã upload |
| POST | `/ask-manual` | Hỏi đáp thủ công (tự truy xuất context) |

### Agent (`/api/v1/agent`)

| Method | Path | Mô tả |
|---|---|---|
| POST | `/coding` | Sinh mã thuật toán đơn giản |
| POST | `/task` | Thực thi tác vụ coding toàn diện (sinh code, test, tự sửa lỗi) |
| POST | `/test` | Chạy unit test trong workspace |
| POST | `/fix` | Agent tự phân tích lỗi, sửa code và test lại |

## Agent Tools

AI Coding Agent được trang bị các công cụ (Spring AI Function Calling):

- **FileSystem** -- tạo, đọc, sửa, xóa file và liệt kê thư mục trong workspace.
- **Terminal** -- thực thi lệnh shell (biên dịch, chạy chương trình).
- **Testing** -- chạy test Java (assertion), Maven test, Python unittest, hoặc test tùy chỉnh.
- **Coding** -- lập kế hoạch thuật toán và lấy code template.
- **Project** -- xem cấu trúc dự án, nhận diện loại project, tìm kiếm mã nguồn.

## Cấu trúc thư mục

```
src/main/java/mycomputer/project/ai_assistant/
├── config/          # Cấu hình ChatClient
├── controller/      # REST Controllers (Chat, RAG, Agent)
├── core/
│   └── tools/       # AI Agent tools (filesystem, terminal, testing, coding, project)
├── dto/             # Request & Response DTOs
├── exception/       # Xử lý lỗi toàn cục
└── service/         # Business logic (ChatAssistantService, RagService, AgentService)
```

## Cấu hình

Tùy chỉnh trong `src/main/resources/application.yaml`:

- **Model chat/coding** -- đổi model Ollama phù hợp với cấu hình máy.
- **Workspace** -- thư mục làm việc của Agent (mặc định: `./workspace`).
- **Database** -- kết nối PostgreSQL (mặc định: `localhost:5433/ragdb`).

## Ghi chú

- Prompt mặc định dùng tiếng Việt, giữ nguyên thuật ngữ tiếng Anh chuyên ngành.
- Agent hoạt động trong sandbox workspace, có cơ chế bảo mật đường dẫn (không truy cập ngoài workspace).
- Có thể mở rộng thêm model hoặc tool tùy nhu cầu.
