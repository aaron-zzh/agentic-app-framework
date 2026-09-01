package com.xuejiai.aaf.framework.intelligent.assistant.model.plan;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * {@link ExecutorPlan} 内的一个可验证步骤。
 *
 * <p><b>步骤不变量（ADR-006）</b>：同一 plan 最多一个 {@code RUNNING}；依赖未完成不得启动；{@code requiredTools}
 * 必须是该 execution 冻结工具白名单的子集（由应用层在 {@code startStep} 时校验，本记录不持有白名单引用）。
 *
 * @param stepKey 板内步骤键，随 plan 生成，{@code UNIQUE(plan_id, step_key)}
 * @param ordinal 步骤在 plan 内的顺序号，{@code UNIQUE(plan_id, ordinal)}；仅用于展示排序，实际调度依据 {@code
 *     dependencies}
 * @param dependencies 依赖的其它 stepKey，只能引用同 plan 内已声明的步骤
 * @param requiredTools 本步骤需要调用的工具名集合
 * @param completionCriteria 人类可读的完成判据，供审计与人工审阅，不参与自动化判定
 * @param resultRef 步骤完成后的结果引用（如证据 ID），不直接持有结果正文
 * @param failureCode 步骤失败时的稳定失败码
 */
public record ExecutorPlanStep(
        String planId,
        String stepKey,
        int ordinal,
        String title,
        String instruction,
        List<String> dependencies,
        List<String> requiredTools,
        List<String> completionCriteria,
        Status status,
        String resultRef,
        String failureCode,
        Instant startedAt,
        Instant finishedAt,
        long lockVersion) {

    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._\\-]{0,127}");

    public ExecutorPlanStep {
        planId = requireSafeKey(planId, "planId");
        stepKey = requireSafeKey(stepKey, "stepKey");
        if (ordinal < 1) {
            throw new IllegalArgumentException("ordinal 必须从 1 开始");
        }
        title = requireText(title, "title");
        instruction = requireText(instruction, "instruction");
        dependencies = List.copyOf(Objects.requireNonNull(dependencies, "dependencies 不能为空"));
        if (dependencies.contains(stepKey)) {
            throw new IllegalArgumentException("步骤不能依赖自身: " + stepKey);
        }
        requiredTools = List.copyOf(Objects.requireNonNull(requiredTools, "requiredTools 不能为空"));
        completionCriteria =
                List.copyOf(
                        Objects.requireNonNull(completionCriteria, "completionCriteria 不能为空"));
        Objects.requireNonNull(status, "status 不能为空");
        if (lockVersion < 0) {
            throw new IllegalArgumentException("lockVersion 不能小于 0");
        }
    }

    /** 依赖是否已全部满足；由应用层在实际调度时结合同 plan 内其它步骤状态判定，本方法只做输入校验的配套读法。 */
    public boolean dependsOn(String otherStepKey) {
        return dependencies.contains(otherStepKey);
    }

    private static String requireSafeKey(String value, String field) {
        var text = requireText(value, field);
        if (!SAFE_KEY.matcher(text).matches()) {
            throw new IllegalArgumentException(field + " 必须是安全键（字母数字开头，仅含 A-Za-z0-9._-）: " + text);
        }
        return text;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " 不能为空");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }

    /** 步骤状态：同 plan 最多一个 {@code RUNNING}。 */
    public enum Status {
        PENDING,
        RUNNING,
        COMPLETED,
        FAILED,
        CANCELLED
    }
}
