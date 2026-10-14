package com.xuejiai.aaf.framework.intelligent.shared.event;

import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TaskId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

import reactor.core.publisher.Flux;

/**
 * 执行事件的实时分发端口。
 *
 * <p>与 {@link ExecutionEventStorePort} 分工不同：事件存储是 append-only 事实来源，只保存有恢复和审计价值的事件；
 * 本端口只负责把执行中产生的事件（含不落库的 {@code MESSAGE_DELTA} 瞬态增量）按出现顺序实时转发给入口订阅方。 订阅方只能看到订阅之后发布的事件，历史事件请读事件存储。
 */
public interface ExecutionLiveEventPort {

    /** 发布一个事件；无 Task 的 DIRECT 事件或无订阅方时直接丢弃。 */
    void publish(ExecutionEvent event);

    /**
     * 打开某个 Task 的实时订阅。
     *
     * <p>调用返回时订阅已登记并开始缓冲，调用方必须在触发执行<b>之前</b>调用，否则会漏掉最早的事件；返回的 Flux 只能被订阅一次，
     * 订阅方取消即移除（调用方在触发执行失败时订阅后立即取消以释放登记）；登记后一直无人订阅的孤儿订阅由实现按时限回收。
     *
     * <p>端口不校验订阅方与 Task 的归属（事件 {@code userId} 对非 HUMAN 责任主体可为空，无法可靠过滤）。归属由调用方保证： 只有在确认当前用户是 Task
     * 创建者 / 审批或澄清关联用户之后，才可把该 Flux 的事件转发给客户端。
     */
    Flux<ExecutionEvent> open(TenantId tenantId, TaskId taskId);
}
