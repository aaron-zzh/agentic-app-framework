package com.xuejiai.aaf.module.ai.aigc.project.api;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 已确认 Proposal 目标的服务端解析结果；仅供 canonical Task/执行桥接冻结，不作为 REST 客户端输入。
 *
 * <p>{@code CREATION_DRAFT} 域尚无项目，因此 {@code projectId}/{@code projectVersion}/{@code
 * graphRevision}/ {@code configurationSnapshot*} 均为空，改由 {@code draftId} + {@code
 * blueprintVersionId} 锚定。
 */
public record AigcApprovedGenerationTarget(
        AigcGenerationProposalScope.Kind scopeKind,
        Long projectId,
        String draftId,
        Long blueprintVersionId,
        Integer projectVersion,
        Long graphRevision,
        Long configurationSnapshotId,
        String configurationSnapshotHash,
        String proposalId,
        Integer proposalVersion,
        String proposalHash,
        String contextHash,
        String itemKey,
        AigcGenerationProposalTargetType targetType,
        String targetKey,
        String actionKey,
        String generationInstruction,
        String instructionHash,
        List<String> dependencyKeys,
        List<String> requiredContextKeys,
        String modelPolicy,
        BigDecimal estimatedCredits,
        String confirmationPolicy,
        Integer itemVersion,
        String itemHash) {

    public AigcApprovedGenerationTarget {
        dependencyKeys = dependencyKeys == null ? List.of() : List.copyOf(dependencyKeys);
        requiredContextKeys =
                requiredContextKeys == null ? List.of() : List.copyOf(requiredContextKeys);
    }

    /**
     * 冻结证据的规范表示。
     *
     * <p>键名在此集中定义，供 {@code AigcExecutionRun.effectiveInput/contextSnapshot} 与执行 submission payload
     * 共用；顺序固定以保证同一目标产生同一证据。
     */
    public Map<String, Object> toEvidence() {
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("scopeKind", scopeKind == null ? null : scopeKind.name());
        evidence.put("projectId", projectId);
        evidence.put("draftId", draftId);
        evidence.put("blueprintVersionId", blueprintVersionId);
        evidence.put("projectVersion", projectVersion);
        evidence.put("graphRevision", graphRevision);
        evidence.put("configurationSnapshotId", configurationSnapshotId);
        evidence.put("configurationSnapshotHash", configurationSnapshotHash);
        evidence.put("proposalId", proposalId);
        evidence.put("proposalVersion", proposalVersion);
        evidence.put("proposalHash", proposalHash);
        evidence.put("contextHash", contextHash);
        evidence.put("itemKey", itemKey);
        evidence.put("targetType", targetType == null ? null : targetType.name());
        evidence.put("targetKey", targetKey);
        evidence.put("actionKey", actionKey);
        evidence.put("generationInstruction", generationInstruction);
        evidence.put("instructionHash", instructionHash);
        evidence.put("dependencyKeys", dependencyKeys);
        evidence.put("requiredContextKeys", requiredContextKeys);
        evidence.put("modelPolicy", modelPolicy);
        evidence.put(
                "estimatedCredits",
                estimatedCredits == null ? null : estimatedCredits.toPlainString());
        evidence.put("confirmationPolicy", confirmationPolicy);
        evidence.put("itemVersion", itemVersion);
        evidence.put("itemHash", itemHash);
        return Collections.unmodifiableMap(evidence);
    }

    /** 从冻结证据恢复目标；证据结构非法时 fail closed，不做部分恢复。 */
    public static AigcApprovedGenerationTarget fromEvidence(Map<String, Object> evidence) {
        Objects.requireNonNull(evidence, "evidence 不能为空");
        return new AigcApprovedGenerationTarget(
                AigcGenerationProposalScope.Kind.valueOf(requireText(evidence, "scopeKind")),
                longValue(evidence, "projectId"),
                text(evidence, "draftId"),
                longValue(evidence, "blueprintVersionId"),
                integerValue(evidence, "projectVersion"),
                longValue(evidence, "graphRevision"),
                longValue(evidence, "configurationSnapshotId"),
                text(evidence, "configurationSnapshotHash"),
                text(evidence, "proposalId"),
                integerValue(evidence, "proposalVersion"),
                text(evidence, "proposalHash"),
                text(evidence, "contextHash"),
                text(evidence, "itemKey"),
                AigcGenerationProposalTargetType.valueOf(requireText(evidence, "targetType")),
                text(evidence, "targetKey"),
                text(evidence, "actionKey"),
                text(evidence, "generationInstruction"),
                text(evidence, "instructionHash"),
                textList(evidence, "dependencyKeys"),
                textList(evidence, "requiredContextKeys"),
                text(evidence, "modelPolicy"),
                decimal(evidence, "estimatedCredits"),
                text(evidence, "confirmationPolicy"),
                integerValue(evidence, "itemVersion"),
                text(evidence, "itemHash"));
    }

    private static String requireText(Map<String, Object> evidence, String key) {
        var value = text(evidence, key);
        if (value == null) {
            throw new IllegalArgumentException("已确认生成目标证据缺少 " + key);
        }
        return value;
    }

    private static String text(Map<String, Object> evidence, String key) {
        var value = evidence.get(key);
        return value == null ? null : String.valueOf(value);
    }

    private static Long longValue(Map<String, Object> evidence, String key) {
        var value = evidence.get(key);
        return value == null ? null : Long.valueOf(String.valueOf(value));
    }

    private static Integer integerValue(Map<String, Object> evidence, String key) {
        var value = evidence.get(key);
        return value == null ? null : Integer.valueOf(String.valueOf(value));
    }

    private static BigDecimal decimal(Map<String, Object> evidence, String key) {
        var value = evidence.get(key);
        return value == null ? null : new BigDecimal(String.valueOf(value));
    }

    private static List<String> textList(Map<String, Object> evidence, String key) {
        var value = evidence.get(key);
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> values)) {
            throw new IllegalArgumentException("已确认生成目标证据 " + key + " 必须为列表");
        }
        return values.stream().map(String::valueOf).toList();
    }
}
