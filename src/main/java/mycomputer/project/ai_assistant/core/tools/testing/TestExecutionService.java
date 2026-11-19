package mycomputer.project.ai_assistant.core.tools.testing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mycomputer.project.ai_assistant.core.tools.filesystem.WorkspaceSecurityService;
import mycomputer.project.ai_assistant.core.tools.terminal.TerminalService;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class TestExecutionService {

    private final TerminalService terminalService;
    private final WorkspaceSecurityService securityService;

    public record TestResult(
            boolean passed,
            int totalTests,
            int passedTests,
            int failedTests,
            String summary,
            String details) {

        public String toFormattedString() {
            StringBuilder sb = new StringBuilder();
            sb.append("=== KẾT QUẢ KIỂM THỬ (TEST RESULT) ===\n");
            sb.append("Trạng thái: ").append(passed ? "THÀNH CÔNG (PASSED)" : "THẤT BẠI (FAILED)").append("\n");
            if (totalTests > 0) {
                sb.append(String.format("Tổng số test: %d | Đạt: %d | Lỗi/Hỏng: %d\n", totalTests, passedTests, failedTests));
            }
            sb.append("Tóm tắt: ").append(summary).append("\n");
            if (details != null && !details.isBlank()) {
                sb.append("Chi tiết lỗi/đầu ra:\n").append(details.trim()).append("\n");
            }
            sb.append("======================================");
            return sb.toString();
        }
    }

    /**
     * Biên dịch và chạy kiểm thử cho Java class sử dụng Java assertions (-ea) hoặc method main test
     */
    public TestResult runJavaAssertionTest(String sourceFile, String testFile) {
        Path srcPath = securityService.resolve(sourceFile);
        Path testPath = securityService.resolve(testFile);

        if (!Files.exists(srcPath)) {
            return new TestResult(false, 0, 0, 1, "File mã nguồn không tồn tại: " + sourceFile, null);
        }
        if (!Files.exists(testPath)) {
            return new TestResult(false, 0, 0, 1, "File kiểm thử không tồn tại: " + testFile, null);
        }

        // Bước 1: Biên dịch cả hai file
        String compileCmd = String.format("javac -encoding UTF-8 \"%s\" \"%s\"",
                securityService.relativize(srcPath), securityService.relativize(testPath));

        TerminalService.CommandResult compileResult = terminalService.execute(compileCmd, "", 30);
        if (!compileResult.isSuccess()) {
            return new TestResult(false, 0, 0, 1,
                    "Lỗi biên dịch Java (Compilation Error)",
                    compileResult.stderr().isBlank() ? compileResult.stdout() : compileResult.stderr());
        }

        // Bước 2: Chạy test class với cờ -ea (Enable Assertions)
        String testClassName = extractClassName(testFile);
        Path testParent = testPath.getParent();
        String workingDir = testParent != null ? securityService.relativize(testParent) : "";

        String runCmd = "java -ea " + testClassName;
        TerminalService.CommandResult runResult = terminalService.execute(runCmd, workingDir, 30);

        if (runResult.isSuccess()) {
            return new TestResult(true, 1, 1, 0,
                    "Tất cả kiểm thử Java assertions đã chạy THÀNH CÔNG!",
                    runResult.stdout());
        } else {
            String errorDetail = !runResult.stderr().isBlank() ? runResult.stderr() : runResult.stdout();
            return new TestResult(false, 1, 0, 1,
                    "Kiểm thử Java thất bại (AssertionError hoặc Exception)",
                    errorDetail);
        }
    }

    /**
     * Chạy unit test trong project Maven (mvn test)
     */
    public TestResult runMavenTests(String projectDir, String testClassName) {
        String cmd;
        if (testClassName != null && !testClassName.isBlank()) {
            cmd = "mvn test -Dtest=" + testClassName.trim();
        } else {
            cmd = "mvn test";
        }

        TerminalService.CommandResult result = terminalService.execute(cmd, projectDir, 90);
        String combinedOutput = result.stdout() + "\n" + result.stderr();

        // Trích xuất thống kê từ output maven: Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
        Pattern pattern = Pattern.compile("Tests run:\\s*(\\d+),\\s*Failures:\\s*(\\d+),\\s*Errors:\\s*(\\d+),\\s*Skipped:\\s*(\\d+)");
        Matcher matcher = pattern.matcher(combinedOutput);

        int total = 0, failures = 0, errors = 0, skipped = 0;
        boolean foundSummary = false;
        while (matcher.find()) {
            foundSummary = true;
            total += Integer.parseInt(matcher.group(1));
            failures += Integer.parseInt(matcher.group(2));
            errors += Integer.parseInt(matcher.group(3));
            skipped += Integer.parseInt(matcher.group(4));
        }

        int failed = failures + errors;
        int passed = total - failed - skipped;
        boolean passedStatus = result.isSuccess() && failed == 0;

        String summary = passedStatus
                ? "Maven tests chạy THÀNH CÔNG!"
                : "Maven tests THẤT BẠI. Cần kiểm tra lại mã nguồn hoặc test case.";

        return new TestResult(passedStatus, total, Math.max(0, passed), failed, summary, combinedOutput);
    }

    /**
     * Chạy kiểm thử Python bằng unittest hoặc pytest
     */
    public TestResult runPythonTest(String testFile) {
        Path path = securityService.resolve(testFile);
        if (!Files.exists(path)) {
            return new TestResult(false, 0, 0, 1, "File kiểm thử Python không tồn tại: " + testFile, null);
        }

        String rel = securityService.relativize(path);
        String cmd = "python -m unittest " + rel;
        TerminalService.CommandResult result = terminalService.execute(cmd, "", 45);

        String output = result.stdout() + "\n" + result.stderr();
        boolean passed = result.isSuccess() && !output.contains("FAILED") && output.contains("OK");

        return new TestResult(passed, 0, passed ? 1 : 0, passed ? 0 : 1,
                passed ? "Python unit test thành công!" : "Python test thất bại",
                output);
    }

    /**
     * Chạy lệnh kiểm thử tùy ý (biên dịch + chạy)
     */
    public TestResult runCustomTest(String compileCommand, String runCommand, String workingDir) {
        if (compileCommand != null && !compileCommand.isBlank()) {
            TerminalService.CommandResult compileRes = terminalService.execute(compileCommand, workingDir, 45);
            if (!compileRes.isSuccess()) {
                return new TestResult(false, 0, 0, 1,
                        "Biên dịch thất bại trước khi test",
                        compileRes.stderr().isBlank() ? compileRes.stdout() : compileRes.stderr());
            }
        }

        TerminalService.CommandResult runRes = terminalService.execute(runCommand, workingDir, 45);
        boolean passed = runRes.isSuccess();
        return new TestResult(passed, 0, passed ? 1 : 0, passed ? 0 : 1,
                passed ? "Lệnh kiểm thử đã hoàn tất thành công." : "Lệnh kiểm thử trả về mã lỗi non-zero.",
                runRes.toFormattedString());
    }

    private String extractClassName(String filePath) {
        String fileName = Path.of(filePath).getFileName().toString();
        if (fileName.endsWith(".java")) {
            return fileName.substring(0, fileName.length() - 5);
        }
        return fileName;
    }
}
