package com.xuejiai.aaf.module.ai.event;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventReducer.PublicState;
import com.xuejiai.aaf.framework.intelligent.shared.event.publication.AafAiTaskEvent;

/** 基于 eventOffset 的任务重放页与状态快照。 */
public record AafAiTaskSnapshot(
        long requestedAfterEventOffset,
        long nextEventOffset,
        String deliveryGuarantee,
        PublicState state,
        List<AafAiTaskEvent> events) {

    public AafAiTaskSnapshot {
        if (requestedAfterEventOffset < 0 || nextEventOffset < requestedAfterEventOffset) {
            throw new IllegalArgumentException("eventOffset cursor 不合法");
        }
        if (!"AT_LEAST_ONCE".equals(deliveryGuarantee)) {
            throw new IllegalArgumentException("仅支持 AT_LEAST_ONCE cursor replay");
        }
        events = events == null ? List.of() : List.copyOf(events);
    }
}
