/**
 * 恢复前置校验默认实现。
 *
 * @author Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant.application;

import java.time.Instant;

import com.xuejiai.aaf.framework.intelligent.assistant.model.Task;
import com.xuejiai.aaf.framework.intelligent.assistant.model.Task.Status;
import com.xuejiai.aaf.framework.intelligent.assistant.model.TaskSnapshot;
import com.xuejiai.aaf.framework.intelligent.assistant.port.RecoveryPreflight;

/**
 * 默认恢复前置校验：任务归属完整性、终态、deadline 与预算三项静态可判定检查。
 *
 * <p>不重新解析 grant/凭证——范围边界见 {@link RecoveryPreflight} 类注释。
 */
public final class DefaultRecoveryPreflight implements RecoveryPreflight {

    @Override
    public Result check(TaskSnapshot task, Instant at) {
        if (task == null) {
            return Result.deny("恢复目标任务不存在");
        }
        if (task.terminal()) {
            return Result.deny("任务已处于终态，不可恢复调度：" + task.status());
        }
        if (task.owner().kind() == Task.OwnerKind.HUMAN) {
            return Result.deny("任务处于人工接管中，不可自动恢复调度");
        }
        if (!task.contract().deadline().isAfter(at)) {
            return Result.deny("任务已超出委托合同 deadline，不可恢复调度");
        }
        var budget = task.contract().budget();
        var usage = task.budgetUsage();
        if (usage.modelTokens() > budget.modelTokens()
                || usage.toolUnits() > budget.toolUnits()
                || usage.credits().compareTo(budget.credits()) > 0) {
            return Result.deny("任务预算已透支，不可恢复调度，须转人工复核额度");
        }
        if (task.status() != Status.READY
                && task.status() != Status.RUNNING
                && task.status() != Status.PAUSED) {
            return Result.deny("任务当前状态不支持恢复调度：" + task.status());
        }
        return Result.allow();
    }
}
