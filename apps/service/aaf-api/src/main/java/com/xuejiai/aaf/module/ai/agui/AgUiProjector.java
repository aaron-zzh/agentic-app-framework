package com.xuejiai.aaf.module.ai.agui;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.ExecutionEventPublicMapper;
import com.xuejiai.aaf.module.ai.agui.converter.PublicEventFallbackConverter;
import com.xuejiai.aaf.module.ai.agui.converter.RunLifecycleEventConverter;
import com.xuejiai.aaf.module.ai.agui.converter.TextMessageEventConverter;

import io.agentscope.core.agui.event.AguiEvent;

/**
 * 内部执行事件到 AG-UI 标准事件的唯一 facade。
 *
 * <p>只做四件事：建 per-run {@link AafAguiStreamContext}、调 {@link AafAguiConverterRegistry} 分派、流尾兜底闭合、
 * 异常路径终结 run。具体投影规则在各 converter 中，配对不变量在 context 中——本类不含任何事件构造逻辑。
 *
 * <p>事件模型使用官方 {@code io.agentscope.core.agui.event.AguiEvent}，不自研——字段契约由前端 {@code @ag-ui/core} 的
 * zod schema 强制校验（{@code EventSchemas.parse}），自研模型无法保证对齐。
 *
 * <p><b>已知局限（AAF-104 #10403）</b>：标准分支（生命周期 / 文本消息）目前直接读内部 {@code ExecutionEvent} 的 payload，只有兜底分支经过
 * {@link ExecutionEventPublicMapper} 脱敏。把全部 converter 的输入统一为公共事件需要公共事件 先暴露 messageId / delta
 * 等字段，属配对契约改造的一部分，与 #10403 一并处理。
 */
@Component
public final class AgUiProjector {

    private final AafAguiConverterRegistry registry;

    public AgUiProjector(ExecutionEventPublicMapper publicMapper) {
        Objects.requireNonNull(publicMapper, "publicMapper 不能为空");
        this.registry =
                new AafAguiConverterRegistry(
                        List.of(new RunLifecycleEventConverter(), new TextMessageEventConverter()),
                        new PublicEventFallbackConverter(publicMapper));
    }

    /** 开启一次 run 的投影会话；配对跟踪与兜底闭合依赖 per-run 状态，禁止跨 run 复用。 */
    public Session openSession() {
        return new Session(registry);
    }

    /** 单次 run 的投影入口。非线程安全，只应被单个事件流串行消费。 */
    public static final class Session {

        private final AafAguiConverterRegistry registry;
        private AafAguiStreamContext context;

        private Session(AafAguiConverterRegistry registry) {
            this.registry = registry;
        }

        /** 投影单个执行事件；无对应 AG-UI 语义的事件由兜底 converter 降级为 aaf.* CUSTOM。 */
        public List<AguiEvent> project(ExecutionEvent event) {
            return registry.convert(event, context(threadId(event), event.runId().value()));
        }

        /** 流结束时兜底：补齐未闭合的 message 与 tool call，并确保 run 已终结。 */
        public List<AguiEvent> close(String threadId, String runId) {
            return context(threadId, runId).close();
        }

        /** 异常路径：发 RUN_ERROR 并闭合 run；AG-UI 要求 run 必须终结。 */
        public List<AguiEvent> fail(String threadId, String runId, String code) {
            return context(threadId, runId).runError(code);
        }

        /**
         * 懒建 context：threadId / runId 由首个事件或调用方给出，两者在一次 run 内恒定。
         *
         * <p>后续调用传入不同标识说明会话被跨 run 复用，直接失败——配对状态一旦跨 run 混用，前端会收到属于另一次运行的 START/END，且无法在事后区分。
         */
        private AafAguiStreamContext context(String threadId, String runId) {
            if (context == null) {
                context = new AafAguiStreamContext(threadId, runId);
                return context;
            }
            if (!context.threadId().equals(threadId) || !context.runId().equals(runId)) {
                throw new IllegalStateException(
                        "AG-UI 投影会话被跨 run 复用: 期望="
                                + context.threadId()
                                + "/"
                                + context.runId()
                                + "，实际="
                                + threadId
                                + "/"
                                + runId);
            }
            return context;
        }

        /**
         * AG-UI 的 threadId 对应 AAF 的 conversationId。
         *
         * <p>{@code AssistantExecutionService.RunIdentity.create} 用同一个 threadId 构造 ConversationId /
         * SessionId，且 {@code ExecutionEvent} 对 conversationId 有非空约束，因此这里必然拿到调用方传入的 threadId。
         */
        private static String threadId(ExecutionEvent event) {
            return event.conversationId().value();
        }
    }
}
