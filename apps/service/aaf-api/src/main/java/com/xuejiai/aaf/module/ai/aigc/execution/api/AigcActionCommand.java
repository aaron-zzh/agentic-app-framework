package com.xuejiai.aaf.module.ai.aigc.execution.api;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.xuejiai.aaf.module.ai.aigc.project.api.AigcApprovedGenerationTarget;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcGenerationProposalScope;

/**
 * 确定性动作提交命令。
 *
 * <p>{@code approvedTarget} 是服务端专用字段：只能由 Proposal→action 桥接在服务端解析后携带，REST 直接动作入口恒为 {@code
 * null}；模型与客户端都不得声明。root Run 冻结该目标，child/retry 原样复制。
 */
public record AigcActionCommand(
        Long projectId,
        Long projectObjectId,
        String actionKey,
        String prompt,
        String requestedModelId,
        Map<String, Object> actionArguments,
        List<Long> attachmentMediaVersionIds,
        List<Long> selectedProjectObjectIds,
        Integer expectedProjectVersion,
        Long expectedGraphRevision,
        boolean confirmed,
        String idempotencyKey,
        AigcApprovedGenerationTarget approvedTarget) {

    public AigcActionCommand {
        Objects.requireNonNull(projectId, "projectId 不能为空");
        actionKey = requireText(actionKey, "actionKey");
        idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
        requestedModelId = optionalText(requestedModelId, "requestedModelId");
        actionArguments =
                actionArguments == null
                        ? Map.of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(actionArguments));
        attachmentMediaVersionIds =
                attachmentMediaVersionIds == null
                        ? List.of()
                        : List.copyOf(attachmentMediaVersionIds);
        selectedProjectObjectIds =
                selectedProjectObjectIds == null
                        ? List.of()
                        : List.copyOf(selectedProjectObjectIds);
        if (approvedTarget != null) {
            if (!actionKey.equals(approvedTarget.actionKey())) {
                throw new IllegalArgumentException("已确认生成目标与动作不一致");
            }
            if (approvedTarget.scopeKind() != AigcGenerationProposalScope.Kind.PROJECT
                    || !projectId.equals(approvedTarget.projectId())) {
                throw new IllegalArgumentException("已确认生成目标必须属于同一已物化项目");
            }
        }
    }

    private static String optionalText(String value, String field) {
        if (value == null) {
            return null;
        }
        return requireText(value, field);
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value;
    }
}
