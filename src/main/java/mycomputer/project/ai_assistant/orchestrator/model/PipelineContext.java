package mycomputer.project.ai_assistant.orchestrator.model;

import lombok.Data;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Data
public class PipelineContext {
    private String pipelineId;
    private String userPrompt;
    private String workspacePath;
    private String subDirectory;

    // Sản phẩm bàn giao lần lượt của 7 Agent:
    private String prdDocument;             // Agent 1: BA
    private String mermaidDiagrams;         // Agent 2: Architect
    private String apiSpecification;        // Agent 3: API Designer
    private List<String> sourceFiles = new ArrayList<>(); // Agent 4: Developer
    private String testResults;             // Agent 5: Tester
    private String securityReport;          // Agent 6: Security Auditor
    private String devopsArtifacts;         // Agent 7: DevOps

    private boolean success;
    private String currentStage;
    private Instant startTime = Instant.now();
    private Instant endTime;
}
