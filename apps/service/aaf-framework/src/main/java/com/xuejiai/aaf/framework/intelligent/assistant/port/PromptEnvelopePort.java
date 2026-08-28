package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptEnvelope;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/**
 * 逐次物理调用信封的追加与读取边界。
 *
 * <p>只允许追加：{@code envelopeSeq} 由存储层按 execution 分配并严格递增，已落库的信封不可修改。恢复取最新一份即可继续， 不要求与执行开始时的画像相同。
 */
public interface PromptEnvelopePort {

    /** 追加一份信封；{@code envelopeSeq} 由实现分配，调用方不传。 */
    PromptEnvelope append(PromptEnvelope.Draft draft);

    Optional<PromptEnvelope> findLatest(TenantId tenantId, ExecutionId executionId);

    List<PromptEnvelope> findAll(TenantId tenantId, ExecutionId executionId);
}
