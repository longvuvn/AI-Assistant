package mycomputer.project.ai_assistant.core.tools.terminal;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TerminalTool {

    private final TerminalService terminalService;

    @Tool(description = "Thực thi lệnh shell/terminal trong thư mục workspace để biên dịch, chạy ứng dụng, chạy kiểm thử, hoặc quản lý gói (ví dụ: 'javac Solution.java', 'java Solution', 'python script.py', 'mvn clean test').")
    public String executeTerminalCommand(
            @ToolParam(description = "Câu lệnh shell/terminal cần thực thi") String command,
            @ToolParam(description = "Thư mục làm việc tương đối trong workspace (để trống nếu chạy ở thư mục gốc)") String workingDir) {
        TerminalService.CommandResult result = terminalService.execute(command, workingDir, 60);
        return result.toFormattedString();
    }
}
