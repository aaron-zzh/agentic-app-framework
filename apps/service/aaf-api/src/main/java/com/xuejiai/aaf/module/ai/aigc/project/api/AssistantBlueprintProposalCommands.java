package com.xuejiai.aaf.module.ai.aigc.project.api;

/** Assistant Blueprint Proposal 命令集合。 */
public final class AssistantBlueprintProposalCommands {

    private AssistantBlueprintProposalCommands() {}

    public record Start(
            String assistantId,
            String instruction,
            AssistantBlueprintCreationContext creationContext) {}

    public record Revise(
            String proposalId, Integer proposalVersion, String proposalHash, String instruction) {}

    public record Confirm(String proposalId, Integer proposalVersion, String proposalHash) {}

    public record Cancel(String proposalId, Integer proposalVersion, String proposalHash) {}

    public record Reference(String proposalId, Integer proposalVersion, String proposalHash) {}
}
