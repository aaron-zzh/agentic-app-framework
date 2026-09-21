package com.xuejiai.aaf.module.ai.aigc.project.api;

/**
 * 协作模式切换命令。
 *
 * <p>只包含 projectId、目标模式、CAS 与幂等键：模式切换不携带任何执行范围、目标或预算参数，也不隐式影响活跃 Task 与已提交 Run——已提交 Run 按创建时冻结的
 * {@code generationMode} 完成，活跃 Task 只能通过显式 Task 控制操作处理。
 */
public record AigcProjectGenerationModeCommand(
        Long projectId, String mode, Integer expectedProjectVersion, String idempotencyKey) {}
