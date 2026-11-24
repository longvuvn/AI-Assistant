package mycomputer.project.ai_assistant.orchestrator.model;

import java.time.Instant;

public record PipelineStageEvent(
        String pipelineId,
        int stageNumber,
        int totalStages,
        AgentRole role,
        AgentStage stageStatus,
        String message,
        Object payload,
        Instant timestamp
) {
    public static PipelineStageEvent of(String pipelineId, int stageNumber, AgentRole role, AgentStage status, String message, Object payload) {
        return new PipelineStageEvent(pipelineId, stageNumber, 7, role, status, message, payload, Instant.now());
    }

    public static PipelineStageEvent of(String pipelineId, int stageNumber, AgentRole role, AgentStage status, String message) {
        return new PipelineStageEvent(pipelineId, stageNumber, 7, role, status, message, null, Instant.now());
    }
}
