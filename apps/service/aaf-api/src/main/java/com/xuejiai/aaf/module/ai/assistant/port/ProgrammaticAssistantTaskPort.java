package com.xuejiai.aaf.module.ai.assistant.port;

import java.util.List;
import java.util.Objects;

/**
 * 受控程序化 canonical Task 入口。
 *
 * <p>业务模块（如 AIGC 项目）只能通过本端口创建 Assistant Task：threadId、canonical Assistant code、结构化 objective 与幂等键
 * 全部由服务端调用方确定，实现内部复用既有 {@code ExecutionIntent}/{@code ExecutionContract}/{@code
 * CompletionCriteria}/{@code InvocationProfile} 装配与 Assistant 可执行性校验。禁止业务模块自行拼装 {@code
 * AssistantCommand} 或绕过该校验。
 */
public interface ProgrammaticAssistantTaskPort {

    /** 只计算受信请求的确定性 Task 身份，不创建 Task。 */
    TaskIdentity identifyTask(Request request);

    /** 按幂等键确定性派生 canonical Task 身份；重放返回同一 Task，不创建第二个 Task。 */
    Receipt submitTask(Request request);

    /** 使用当前服务端身份取消 canonical Task；不存在或已终态时幂等返回。 */
    void cancelTask(String taskId, String reason);

    /**
     * 读取 canonical Task 的只读状态快照。
     *
     * <p>状态真理源是 canonical Task 本身；业务模块据此做"同项目单任务"互斥与状态展示，不得另建任务状态机或缓存状态。Task 不存在时 返回空——不存在即不占用。
     */
    java.util.Optional<TaskSnapshot> findTask(String taskId);

    /**
     * canonical Task 只读状态快照。
     *
     * <p>{@code terminal} 由 canonical 状态判定，调用方不重复推导；{@code decisionBlockers} 是该 Task 仍在等待外部结果、 且按权威
     * evaluator 判定需要人工决策时的阻断原因，为空表示无需人工介入。
     */
    record TaskSnapshot(
            String taskId,
            String status,
            boolean terminal,
            java.util.List<String> decisionBlockers) {

        public TaskSnapshot {
            taskId = requireText(taskId, "taskId");
            status = requireText(status, "status");
            decisionBlockers = decisionBlockers == null ? List.of() : List.copyOf(decisionBlockers);
        }
    }

    /** 服务端受信代码可选择的程序化目的；每种目的固定 Role/Skill，不接受客户端或模型覆盖。 */
    enum Purpose {
        PROJECT_GENERATION(
                "system.role.content-creator", "system.skill.content-project-collaboration", true),
        BLUEPRINT_PLANNING(
                "system.role.content-creator", "system.skill.assistant-blueprint-planning", false);

        private final String roleKey;
        private final String skillKey;
        private final boolean projectScopeAllowed;

        Purpose(String roleKey, String skillKey, boolean projectScopeAllowed) {
            this.roleKey = roleKey;
            this.skillKey = skillKey;
            this.projectScopeAllowed = projectScopeAllowed;
        }

        public String roleKey() {
            return roleKey;
        }

        public String skillKey() {
            return skillKey;
        }

        /**
         * 该目的是否允许携带受信 projectId。
         *
         * <p>项目生成在项目域携带真实项目、在物化前草稿域没有项目，因此允许为空；Blueprint 规划固定发生在物化前，必须为空。
         */
        public boolean projectScopeAllowed() {
            return projectScopeAllowed;
        }
    }

    /**
     * 程序化任务请求；purpose 决定固定执行策略，不接受模型或客户端提供执行画像、合同与权限范围。
     *
     * <p>{@code projectId} 是服务端受信项目范围：由调用方在自己的事务内解析真实项目，随执行画像冻结后供项目 Tool 使用； 模型不得声明也不得覆盖。{@code
     * Purpose#projectScopeAllowed()} 为 false 的目的必须为 {@code null}。
     */
    record Request(
            String assistantId,
            String threadId,
            String idempotencyKey,
            Purpose purpose,
            Long projectId,
            Objective objective) {

        public Request {
            assistantId = requireText(assistantId, "assistantId");
            threadId = requireText(threadId, "threadId");
            idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
            Objects.requireNonNull(purpose, "purpose 不能为空");
            Objects.requireNonNull(objective, "objective 不能为空");
            if (projectId != null && !purpose.projectScopeAllowed()) {
                throw new IllegalArgumentException("该程序化目的不允许携带 projectId");
            }
            if (projectId != null && projectId < 1) {
                throw new IllegalArgumentException("projectId 必须为正数");
            }
            if (!purpose.roleKey().equals(objective.roleKey())
                    || !purpose.skillKey().equals(objective.skillKey())) {
                throw new IllegalArgumentException("程序化任务 purpose 与固定 Role/Skill 不一致");
            }
        }
    }

    /** 预计算的确定性 canonical Task 身份。 */
    record TaskIdentity(String taskId) {
        public TaskIdentity {
            taskId = requireText(taskId, "taskId");
        }
    }

    /**
     * 结构化目标。
     *
     * <p>{@code summary} 只作任务摘要；{@code generationInstruction} 是独立冻结的目标指令，必须与其来源引用 {@code
     * instructionRef} 同时给出或同时省略。
     */
    record Objective(
            String summary,
            String generationInstruction,
            String instructionRef,
            String roleKey,
            String skillKey,
            List<Fact> facts) {

        public Objective {
            summary = requireText(summary, "objective.summary");
            roleKey = requireText(roleKey, "objective.roleKey");
            skillKey = requireText(skillKey, "objective.skillKey");
            generationInstruction = trimToNull(generationInstruction);
            instructionRef = trimToNull(instructionRef);
            if ((generationInstruction == null) != (instructionRef == null)) {
                throw new IllegalArgumentException("目标指令与其来源引用必须同时给出");
            }
            facts = facts == null ? List.of() : List.copyOf(facts);
        }
    }

    /** 服务端冻结的只读事实；作为不可信任务材料进入上下文，不授予任何权限。 */
    record Fact(String key, String version, String summary, String text) {

        public Fact {
            key = requireText(key, "fact.key");
            version = requireText(version, "fact.version");
            summary = trimToNull(summary);
            text = requireText(text, "fact.text");
        }
    }

    /** canonical Task 身份回执；{@code created=false} 表示幂等重放命中既有 Task。 */
    record Receipt(String taskId, String threadId, String status, boolean created) {

        public Receipt {
            taskId = requireText(taskId, "taskId");
            threadId = requireText(threadId, "threadId");
            status = requireText(status, "status");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " 不能为空白");
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
