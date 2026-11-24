package mycomputer.project.ai_assistant.dto.response.orchestrator;

import mycomputer.project.ai_assistant.orchestrator.model.PipelineContext;

import java.time.Instant;
import java.util.List;

public record PipelineResponse(
        String pipelineId,
        boolean success,
        String summary,
        List<String> affectedFiles,
        String prdDocument,
        String mermaidDiagrams,
        String apiSpecification,
        String testResults,
        String securityReport,
        String devopsArtifacts,
        Instant completedAt
) {
    public static PipelineResponse from(PipelineContext ctx, String summary) {
        return new PipelineResponse(
                ctx.getPipelineId(),
                ctx.isSuccess(),
                summary,
                ctx.getSourceFiles(),
                ctx.getPrdDocument(),
                ctx.getMermaidDiagrams(),
                ctx.getApiSpecification(),
                ctx.getTestResults(),
                ctx.getSecurityReport(),
                ctx.getDevopsArtifacts(),
                Instant.now()
        );
    }
}
