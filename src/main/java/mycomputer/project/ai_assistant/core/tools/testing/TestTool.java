package mycomputer.project.ai_assistant.core.tools.testing;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TestTool {

    private final TestExecutionService testExecutionService;

    @Tool(description = "Biên dịch và chạy kiểm thử tự động cho Java class có chứa assertions (-ea) hoặc hàm main kiểm thử. Trả về kết quả pass/fail và stack trace chi tiết nếu có lỗi.")
    public String runJavaAssertionTest(
            @ToolParam(description = "Đường dẫn file mã nguồn Java chính (ví dụ: 'Solution.java' hoặc 'algorithms/Dijkstra.java')") String sourceFile,
            @ToolParam(description = "Đường dẫn file test Java (ví dụ: 'SolutionTest.java')") String testFile) {
        TestExecutionService.TestResult result = testExecutionService.runJavaAssertionTest(sourceFile, testFile);
        return result.toFormattedString();
    }

    @Tool(description = "Chạy bộ kiểm thử Maven (mvn test) trong thư mục dự án và phân tích chi tiết số test pass, fail, error.")
    public String runMavenTest(
            @ToolParam(description = "Thư mục chứa file pom.xml (để trống nếu ở thư mục gốc)") String projectDir,
            @ToolParam(description = "Tên class test cụ thể cần chạy (ví dụ: 'UserServiceTest', hoặc để trống để chạy tất cả)") String testClassName) {
        TestExecutionService.TestResult result = testExecutionService.runMavenTests(projectDir, testClassName);
        return result.toFormattedString();
    }

    @Tool(description = "Chạy kiểm thử Python tự động bằng unittest runner.")
    public String runPythonTest(
            @ToolParam(description = "Đường dẫn file test Python (ví dụ: 'test_solution.py')") String testFile) {
        TestExecutionService.TestResult result = testExecutionService.runPythonTest(testFile);
        return result.toFormattedString();
    }

    @Tool(description = "Chạy kiểm thử với lệnh biên dịch và lệnh chạy tùy biến. Rất hữu ích cho C++, Go, Rust hoặc script kiểm tra.")
    public String runCustomTest(
            @ToolParam(description = "Lệnh biên dịch nếu cần (ví dụ: 'g++ -o test test.cpp'), để trống nếu là ngôn ngữ thông dịch") String compileCommand,
            @ToolParam(description = "Lệnh chạy kiểm thử (ví dụ: './test')") String runCommand,
            @ToolParam(description = "Thư mục làm việc tương đối") String workingDir) {
        TestExecutionService.TestResult result = testExecutionService.runCustomTest(compileCommand, runCommand, workingDir);
        return result.toFormattedString();
    }
}
