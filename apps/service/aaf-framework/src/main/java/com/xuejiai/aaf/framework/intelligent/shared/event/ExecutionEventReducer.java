package com.xuejiai.aaf.framework.intelligent.shared.event;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEvent.ExecutionEventStatus;
import com.xuejiai.aaf.framework.intelligent.shared.event.ExecutionEventStorePort.StoredExecutionEvent;

/** 执行事件的确定性状态 reducer；同步结果和任务快照必须共享此模型。 */
public final class ExecutionEventReducer {

    private ExecutionEventReducer() {}

    public static State reduce(List<ExecutionEvent> events) {
        var state = State.empty();
        for (var event : events) {
            state = apply(state, event, null);
        }
        return state;
    }

    public static State reduceStored(List<StoredExecutionEvent> events) {
        var state = State.empty();
        for (var stored : events) {
            state = apply(state, stored.event(), stored.eventOffset());
        }
        return state;
    }

    public static State apply(State state, StoredExecutionEvent stored) {
        return apply(state, stored.event(), stored.eventOffset());
    }

    public static State apply(State state, ExecutionEvent event) {
        return apply(state, event, null);
    }

    private static State apply(State current, ExecutionEvent event, Long eventOffset) {
        var resultText = current.resultText();
        if (event.type() == ExecutionEventType.MESSAGE_DELTA) {
            var delta = event.payload().values().get("delta");
            if (delta instanceof String text) {
                resultText = resultText + text;
            }
        } else if (event.type() == ExecutionEventType.MESSAGE_COMPLETED) {
            var text = event.payload().values().get("text");
            if (text instanceof String completed) {
                resultText = completed;
            }
        }
        return new State(
                event.taskId() == null ? null : event.taskId().value(),
                event.executionId().value(),
                event.runId().value(),
                event.sessionId().value(),
                event.status(),
                event.sequence(),
                eventOffset == null ? current.eventOffset() : eventOffset,
                taskTerminal(event.type()),
                resultText,
                current.eventCount() + 1);
    }

    private static boolean taskTerminal(ExecutionEventType type) {
        return switch (type) {
            case EXECUTION_PROMOTED,
                    EXECUTION_COMPLETED,
                    EXECUTION_FAILED,
                    EXECUTION_CANCELED,
                    COMMAND_REJECTED ->
                    true;
            default -> false;
        };
    }

    public record State(
            String taskId,
            String executionId,
            String runId,
            String sessionId,
            ExecutionEventStatus status,
            long sequence,
            Long eventOffset,
            boolean terminal,
            String resultText,
            long eventCount) {

        public State {
            resultText = resultText == null ? "" : resultText;
        }

        public static State empty() {
            return new State(
                    null, null, null, null, ExecutionEventStatus.DRAFT, 0, null, false, "", 0);
        }

        public PublicState publicState() {
            return new PublicState(
                    taskId,
                    executionId,
                    runId,
                    sessionId,
                    status.name(),
                    sequence,
                    eventOffset,
                    terminal,
                    eventCount);
        }
    }

    /** 不含正文的公开状态快照。 */
    public record PublicState(
            String taskId,
            String executionId,
            String runId,
            String sessionId,
            String status,
            long sequence,
            Long eventOffset,
            boolean terminal,
            long eventCount) {}
}
