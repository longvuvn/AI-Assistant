package mycomputer.project.ai_assistant;

import mycomputer.project.ai_assistant.core.tools.filesystem.FileSystemService;
import mycomputer.project.ai_assistant.core.tools.filesystem.WorkspaceSecurityService;
import mycomputer.project.ai_assistant.core.tools.project.ProjectAnalysisService;
import mycomputer.project.ai_assistant.core.tools.terminal.TerminalService;
import mycomputer.project.ai_assistant.core.tools.testing.TestExecutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AgentToolsTest {

    @TempDir
    Path tempWorkspace;

    private WorkspaceSecurityService securityService;
    private FileSystemService fileSystemService;
    private TerminalService terminalService;
    private TestExecutionService testExecutionService;
    private ProjectAnalysisService projectAnalysisService;

    @BeforeEach
    void setUp() {
        securityService = new WorkspaceSecurityService(tempWorkspace.toString());
        fileSystemService = new FileSystemService(securityService);
        terminalService = new TerminalService(securityService);
        testExecutionService = new TestExecutionService(terminalService, securityService);
        projectAnalysisService = new ProjectAnalysisService(securityService);
    }

    @Test
    void testWorkspaceSecurityPreventsTraversal() {
        assertThrows(SecurityException.class, () -> securityService.resolve("../../windows/system32"));
        assertNotNull(securityService.resolve("src/Main.java"));
    }

    @Test
    void testFileSystemServiceCrud() {
        String createRes = fileSystemService.create("test/Hello.txt", "Xin chào thạc sĩ CNTT!");
        assertTrue(createRes.contains("Thành công"));
        assertTrue(fileSystemService.exists("test/Hello.txt"));

        String content = fileSystemService.read("test/Hello.txt");
        assertEquals("Xin chào thạc sĩ CNTT!", content);

        String editRes = fileSystemService.edit("test/Hello.txt", "thạc sĩ", "tiến sĩ");
        assertTrue(editRes.contains("Thành công"));
        assertEquals("Xin chào tiến sĩ CNTT!", fileSystemService.read("test/Hello.txt"));

        String listRes = fileSystemService.list("test");
        assertTrue(listRes.contains("Hello.txt"));

        String deleteRes = fileSystemService.delete("test/Hello.txt");
        assertTrue(deleteRes.contains("Thành công"));
        assertFalse(fileSystemService.exists("test/Hello.txt"));
    }

    @Test
    void testTerminalServiceEchoAndDangerousBlocks() {
        TerminalService.CommandResult result = terminalService.execute("echo Hello Agent", "", 10);
        assertTrue(result.isSuccess() || !result.stdout().isBlank());

        TerminalService.CommandResult dangerous = terminalService.execute("format C:", "", 10);
        assertFalse(dangerous.isSuccess());
        assertTrue(dangerous.stderr().contains("nguy hiểm"));
    }

    @Test
    void testJavaTestExecution() {
        // Tạo class tính giai thừa
        String solutionCode = """
                public class Factorial {
                    public static long fact(int n) {
                        if (n < 0) throw new IllegalArgumentException("Negative");
                        if (n <= 1) return 1;
                        return n * fact(n - 1);
                    }
                }
                """;
        fileSystemService.create("Factorial.java", solutionCode);

        // Tạo test class với assert
        String testCode = """
                public class FactorialTest {
                    public static void main(String[] args) {
                        assert Factorial.fact(0) == 1 : "fact(0) must be 1";
                        assert Factorial.fact(5) == 120 : "fact(5) must be 120";
                        System.out.println("All Factorial tests passed!");
                    }
                }
                """;
        fileSystemService.create("FactorialTest.java", testCode);

        TestExecutionService.TestResult testResult = testExecutionService.runJavaAssertionTest("Factorial.java", "FactorialTest.java");
        assertTrue(testResult.passed(), "Test should pass: " + testResult.details());
        assertEquals(1, testResult.passedTests());
        assertEquals(0, testResult.failedTests());
    }

    @Test
    void testProjectAnalysis() {
        fileSystemService.create("module/App.java", "public class App {}");
        fileSystemService.create("module/AppTest.java", "public class AppTest {}");

        String tree = projectAnalysisService.getProjectTree("", 3);
        assertTrue(tree.contains("App.java"));

        String search = projectAnalysisService.searchInFiles("class App", ".java");
        assertTrue(search.contains("App.java"));
    }
}
