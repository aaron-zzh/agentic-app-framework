package com.xuejiai.aaf.module.ai.agui.converter;

import java.util.List;
import java.util.Set;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventType;
import com.xuejiai.aaf.module.ai.agui.AafAguiEventConverter;
import com.xuejiai.aaf.module.ai.agui.AafAguiStreamContext;

import io.agentscope.core.agui.event.AguiEvent;

/**
 * run 生命周期与终态投影。
 *
 * <p>{@code RUN_FAILED} 是叶子 Agent 失败，{@code EXECUTION_*} 是任务级终态；两者都必须收敛为 AG-UI 的 {@code
 * RUN_ERROR}，否则前端只能收到 CUSTOM，无法识别失败。错误码只暴露稳定枚举字符串，不带异常原文。
 */
public final class RunLifecycleEventConverter implements AafAguiEventConverter {

    @Override
    public Set<ExecutionEventType> supportedTypes() {
        return Set.of(
                ExecutionEventType.EXECUTION_STARTED,
                ExecutionEventType.EXECUTION_COMPLETED,
                ExecutionEventType.EXECUTION_FAILED,
                ExecutionEventType.EXECUTION_CANCELED,
                ExecutionEventType.COMMAND_REJECTED,
                ExecutionEventType.RUN_FAILED);
    }

    @Override
    public List<AguiEvent> convert(ExecutionEvent event, AafAguiStreamContext context) {
        return switch (event.type()) {
            case EXECUTION_STARTED -> context.runStarted();
            case EXECUTION_COMPLETED -> context.runFinishedOnce();
            case EXECUTION_FAILED, EXECUTION_CANCELED, COMMAND_REJECTED, RUN_FAILED ->
                    context.runError(errorCode(event));
            default ->
                    throw new IllegalStateException(
                            "RunLifecycleEventConverter 收到未声明支持的类型: " + event.type());
        };
    }

    private static String errorCode(ExecutionEvent event) {
        return switch (event.type()) {
            case EXECUTION_CANCELED -> "RUN_CANCELED";
            case COMMAND_REJECTED -> "ASSISTANT_COMMAND_REJECTED";
            case RUN_FAILED -> "AGENT_RUN_FAILED";
            default -> "ASSISTANT_EXECUTION_FAILED";
        };
    }
}
