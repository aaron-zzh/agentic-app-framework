package com.xuejiai.aaf.framework.intelligent.cognition.pipeline;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryDeduplicationService;
import com.xuejiai.aaf.framework.intelligent.cognition.memory.MemoryExtractionService;
import com.xuejiai.aaf.framework.intelligent.core.memory.MemoryWritePipeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 写管道默认实现——固定四步流程，不可跳过。
 *
 * <pre>
 * 提取（MemoryExtractionService）
 *   ↓ 从对话中抽取值得记忆的片段（实体/偏好/决策/情感）
 * 去重（MemoryDeduplicationService）
 *   ↓ 语义相似度比对，合并/更新已有记忆
 * 写入（AtomMemoryEngine，由 MemoryExtractionService 内部调用）
 *   ↓ 原子化存储，双时态索引
 * 遗忘（TimeDecayStrategy，异步触发）
 *   低权重旧记忆降权，高价值记忆永久保留
 * </pre>
 *
 * TODO: 遗忘步骤当前为占位，待 AtomMemoryEngine 遗忘策略完善后接入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultMemoryWritePipeline implements MemoryWritePipeline {

    private final MemoryExtractionService extractionService;
    private final MemoryDeduplicationService deduplicationService;

    @Override
    public void execute(WriteInput input) {
        // 步骤 1：提取
        var extracted = extractionService.extract(
            input.userMessage(), input.assistantReply(), input.userId(), input.sessionId()
        );
        if (extracted.isEmpty()) {
            log.debug("写管道：无值得记忆的片段，跳过后续步骤 sessionId={}", input.sessionId());
            return;
        }

        // 步骤 2：去重（合并/更新已有记忆）
        var deduplicated = deduplicationService.deduplicate(extracted, input.userId());

        // 步骤 3：写入（由 MemoryExtractionService 内部持久化，此处记录日志）
        log.debug("写管道：写入 {} 条记忆 userId={} sessionId={}",
            deduplicated.size(), input.userId(), input.sessionId());

        // 步骤 4：遗忘（异步，TODO: 接入 TimeDecayStrategy）
    }
}
