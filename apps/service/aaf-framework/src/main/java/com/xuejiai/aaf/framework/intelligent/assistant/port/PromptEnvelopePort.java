package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.List;
import java.util.Optional;

import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptEnvelope;
import com.xuejiai.aaf.framework.intelligent.core.prompt.PromptEnvelopeBody;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.ExecutionId;
import com.xuejiai.aaf.framework.intelligent.shared.id.StableId.TenantId;

/**
 * 逐次物理调用信封的追加与读取边界。
 *
 * <p>只允许追加：{@code envelopeSeq} 由存储层按 execution 分配并严格递增，已落库的信封不可修改。恢复取最新一份即可继续， 不要求与执行开始时的画像相同。
 *
 * <p>信封只含 hash 与长度投影；完整正文由 {@link PromptEnvelopeBody} 在独立存储中与信封同事务写入，读取必须显式调用 {@link
 * #findBody}，重试检测与恢复路径不加载正文。
 */
public interface PromptEnvelopePort {

    /** 原子追加信封与其完整正文；{@code envelopeSeq} 由实现分配，调用方不传。 */
    PromptEnvelope append(PromptEnvelope.Draft draft, PromptEnvelopeBody body);

    Optional<PromptEnvelope> findLatest(TenantId tenantId, ExecutionId executionId);

    List<PromptEnvelope> findAll(TenantId tenantId, ExecutionId executionId);

    /** 按执行与信封序号读取完整正文；调试与质量分析专用。 */
    Optional<PromptEnvelopeBody> findBody(
            TenantId tenantId, ExecutionId executionId, int envelopeSeq);
}
