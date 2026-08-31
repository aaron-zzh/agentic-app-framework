/**
 * 恢复前置校验边界。
 *
 * @author Kiro
 */
package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.time.Instant;
import java.util.Objects;

import com.xuejiai.aaf.framework.intelligent.assistant.model.DelegatedTask;

/**
 * 恢复动作生效前的统一前置校验入口（方案 C，2026-08-29 拍板）。
 *
 * <p><b>范围边界</b>：本端口只覆盖恢复时能够脱离具体工具调用静态判定的部分——任务归属与终态、预算是否已透支、 deadline 是否已过期。授权 grant、凭证 scope
 * 这类细粒度校验天然依赖具体的 action/resource/connectorId 参数，不可能在“是否允许恢复这个任务”的粒度上穷举校验；它们继续在下一次工具调用时由 {@code
 * DefaultToolGateway} 校验（既有实现，不受本端口影响）。这是对 {@code task-durability.md} “统一
 * RecoveryPreflight”目标态的务实收窄，不是完整的动态授权重校验闭环。
 *
 * <p>任一检查项不通过即 fail-closed，返回带原因的 {@link Result#deny(String)}，调用方不得放行恢复调度。
 */
public interface RecoveryPreflight {

    /** 对给定任务的当前持久状态做恢复前检查；不产生副作用，不修改任何状态。 */
    Result check(DelegatedTask task, Instant at);

    /** 校验结果；{@code allowed=false} 时 {@code reason} 必须非空，用于审计与转人工提示。 */
    record Result(boolean allowed, String reason) {
        public Result {
            if (!allowed) {
                Objects.requireNonNull(reason, "拒绝结果必须携带原因");
                if (reason.isBlank()) {
                    throw new IllegalArgumentException("拒绝原因不能为空白");
                }
            }
        }

        public static Result allow() {
            return new Result(true, null);
        }

        public static Result deny(String reason) {
            return new Result(false, reason);
        }
    }
}
