package com.xuejiai.aaf.framework.intelligent.cognition.port;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.agent.model.AgentMessage;
import com.xuejiai.aaf.framework.intelligent.cognition.model.MemoryRecord.SourceReference;
import com.xuejiai.aaf.framework.intelligent.cognition.port.MemoryRecallPort.RecallQuery;

/** Agent 调用前的记忆上下文组装边界。 */
public interface MemoryContextPort {

    MemoryContext prepare(RecallQuery query);

    record MemoryContext(List<AgentMessage> messages, List<SourceReference> references) {
        public MemoryContext {
            messages = messages == null ? List.of() : List.copyOf(messages);
            references = references == null ? List.of() : List.copyOf(references);
        }

        public static MemoryContext empty() {
            return new MemoryContext(List.of(), List.of());
        }
    }
}
