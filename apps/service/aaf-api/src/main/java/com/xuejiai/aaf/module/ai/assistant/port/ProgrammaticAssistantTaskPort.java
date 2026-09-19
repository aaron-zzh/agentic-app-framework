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

    /** 按幂等键确定性派生 canonical Task 身份；重放返回同一 Task，不创建第二个 Task。 */
    Receipt submitTask(Request request);

    /** 程序化任务请求；不接受模型或客户端提供的执行画像、合同与产物策略。 */
    record Request(
            String assistantId, String threadId, String idempotencyKey, Objective objective) {

        public Request {
            assistantId = requireText(assistantId, "assistantId");
            threadId = requireText(threadId, "threadId");
            idempotencyKey = requireText(idempotencyKey, "idempotencyKey");
            Objects.requireNonNull(objective, "objective 不能为空");
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
