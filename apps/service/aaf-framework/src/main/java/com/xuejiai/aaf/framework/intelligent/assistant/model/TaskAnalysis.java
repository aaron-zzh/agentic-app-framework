package com.xuejiai.aaf.framework.intelligent.assistant.model;

import java.util.Objects;

/**
 * 任务复杂度判定结果——选择 TaskBoard 类型的唯一依据。
 *
 * <p>编排形态由本判定决定，<b>不由 {@code interactionMode} 绑定</b>：交互模式只决定澄清方式、交互焦点与呈现方式。判定结果随执行画像冻结并落成审计事实。
 *
 * <p>完整契约见 {@code docs/design/framework/intelligent/runtime.md} 的「任务复杂度判定」。
 */
public record TaskAnalysis(
        OwnerMode ownerMode,
        ProcessMode processMode,
        CoordinationMode coordinationMode,
        String rationale,
        AnalyzedBy analyzedBy) {

    public TaskAnalysis {
        Objects.requireNonNull(ownerMode, "ownerMode 不能为空");
        Objects.requireNonNull(processMode, "processMode 不能为空");
        Objects.requireNonNull(coordinationMode, "coordinationMode 不能为空");
        Objects.requireNonNull(analyzedBy, "analyzedBy 不能为空");
        rationale = requireText(rationale, "rationale");
        if (ownerMode == OwnerMode.DIRECT && coordinationMode != CoordinationMode.NONE) {
            throw new IllegalArgumentException("DIRECT 不得声明协调形态");
        }
        if (ownerMode == OwnerMode.DELEGATE && coordinationMode == CoordinationMode.NONE) {
            throw new IllegalArgumentException("DELEGATE 至少需要一个执行节点");
        }
    }

    /** 谁承担执行：助理直答还是委派执行体。 */
    public enum OwnerMode {
        DIRECT,
        DELEGATE
    }

    /** 过程形态：自主推进还是已发布工作流。 */
    public enum ProcessMode {
        AUTONOMOUS,
        PREDEFINED_WORKFLOW
    }

    /** 协调形态：决定 single 还是 coordinated。 */
    public enum CoordinationMode {
        NONE,
        SINGLE_AGENT,
        TASKBOARD
    }

    /** 判定方式：确定性短路不产生模型调用。 */
    public enum AnalyzedBy {
        DETERMINISTIC,
        MODEL
    }

    /** 是否需要协调编排看板。 */
    public boolean requiresTaskBoard() {
        return coordinationMode == CoordinationMode.TASKBOARD;
    }

    /** 确定性单执行体判定。 */
    public static TaskAnalysis singleAgent(String rationale) {
        return new TaskAnalysis(
                OwnerMode.DELEGATE,
                ProcessMode.AUTONOMOUS,
                CoordinationMode.SINGLE_AGENT,
                rationale,
                AnalyzedBy.DETERMINISTIC);
    }

    /** 确定性协调编排判定。 */
    public static TaskAnalysis taskBoard(String rationale) {
        return new TaskAnalysis(
                OwnerMode.DELEGATE,
                ProcessMode.AUTONOMOUS,
                CoordinationMode.TASKBOARD,
                rationale,
                AnalyzedBy.DETERMINISTIC);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }
}
