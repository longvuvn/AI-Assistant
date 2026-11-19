package mycomputer.project.ai_assistant.core.tools.terminal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mycomputer.project.ai_assistant.core.tools.filesystem.WorkspaceSecurityService;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TerminalService {

    private final WorkspaceSecurityService securityService;

    private static final List<String> DANGEROUS_COMMANDS = Arrays.asList(
            "format", "del /f /s /q c:", "rmdir /s /q c:", "shutdown", "reboot",
            ":(){ :|:& };:", "drop database", "mkfs", "dd if="
    );

    public record CommandResult(int exitCode, String stdout, String stderr, long durationMs, boolean timedOut) {
        public boolean isSuccess() {
            return exitCode == 0 && !timedOut;
        }

        public String toFormattedString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Exit Code: ").append(exitCode);
            if (timedOut) {
                sb.append(" (HẾT THỜI GIAN CHỜ - TIMED OUT)\n");
            } else {
                sb.append(" (Thời gian: ").append(durationMs).append("ms)\n");
            }
            if (!stdout.isBlank()) {
                sb.append("--- STDOUT ---\n").append(stdout.trim()).append("\n");
            }
            if (!stderr.isBlank()) {
                sb.append("--- STDERR ---\n").append(stderr.trim()).append("\n");
            }
            return sb.toString();
        }
    }

    public CommandResult execute(String command, String relativeWorkingDir, int timeoutSeconds) {
        if (command == null || command.isBlank()) {
            return new CommandResult(-1, "", "Lỗi: Lệnh không được để trống.", 0, false);
        }

        String lowerCmd = command.toLowerCase(Locale.ROOT);
        for (String dangerous : DANGEROUS_COMMANDS) {
            if (lowerCmd.contains(dangerous)) {
                return new CommandResult(-1, "", "Từ chối thực thi: Lệnh chứa từ khóa nguy hiểm (" + dangerous + ").", 0, false);
            }
        }

        Path workPath = securityService.resolve(relativeWorkingDir);
        File workDir = workPath.toFile();
        if (!workDir.exists()) {
            workDir.mkdirs();
        }

        long startTime = System.currentTimeMillis();
        boolean isWindows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        ProcessBuilder processBuilder;

        if (isWindows) {
            processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            processBuilder = new ProcessBuilder("sh", "-c", command);
        }

        processBuilder.directory(workDir);

        Process process = null;
        try {
            process = processBuilder.start();
            final Process proc = process;

            ExecutorService streamExecutor = Executors.newFixedThreadPool(2);
            Future<String> stdoutFuture = streamExecutor.submit(() -> readStream(proc.getInputStream()));
            Future<String> stderrFuture = streamExecutor.submit(() -> readStream(proc.getErrorStream()));

            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            long duration = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                streamExecutor.shutdownNow();
                return new CommandResult(-1, "", "Lệnh bị hủy do chạy quá thời gian (" + timeoutSeconds + "s).", duration, true);
            }

            String stdout = stdoutFuture.get(5, TimeUnit.SECONDS);
            String stderr = stderrFuture.get(5, TimeUnit.SECONDS);
            streamExecutor.shutdown();

            return new CommandResult(process.exitValue(), stdout, stderr, duration, false);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (process != null) process.destroyForcibly();
            return new CommandResult(-1, "", "Tiến trình bị gián đoạn: " + e.getMessage(), System.currentTimeMillis() - startTime, false);
        } catch (Exception e) {
            log.error("Lỗi khi thực thi lệnh: {}", command, e);
            if (process != null) process.destroyForcibly();
            return new CommandResult(-1, "", "Lỗi thực thi lệnh: " + e.getMessage(), System.currentTimeMillis() - startTime, false);
        }
    }

    private String readStream(InputStream is) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            int lineCount = 0;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
                lineCount++;
                if (lineCount > 500) {
                    sb.append("... [Đầu ra quá dài, đã rút gọn] ...\n");
                    break;
                }
            }
            return sb.toString();
        } catch (Exception e) {
            return "[Lỗi đọc output stream: " + e.getMessage() + "]";
        }
    }
}
