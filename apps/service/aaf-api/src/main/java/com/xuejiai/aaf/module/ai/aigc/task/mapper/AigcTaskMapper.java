package com.xuejiai.aaf.module.ai.aigc.task.mapper;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaApi;
import com.xuejiai.aaf.module.ai.aigc.media.api.AigcMediaView;
import com.xuejiai.aaf.module.ai.aigc.task.domain.AigcTask;
import com.xuejiai.aaf.module.ai.aigc.task.vo.AigcTaskVO;

import lombok.RequiredArgsConstructor;

/** AIGC 任务对象转换器。 */
@Component
@RequiredArgsConstructor
public class AigcTaskMapper {

    private final AigcMediaApi mediaApi;

    public AigcTaskVO toVO(AigcTask task) {
        AigcMediaView media =
                task.getOutputMediaVersionId() != null
                        ? mediaApi.getByVersionId(task.getOutputMediaVersionId(), task.getUserId())
                        : null;
        return new AigcTaskVO(
                task.getId(),
                task.getUserId(),
                task.getType(),
                task.getStatus(),
                task.getProvider(),
                task.getProviderKey(),
                task.getModel(),
                task.getPrompt(),
                task.getProviderTaskId(),
                task.getProviderResult(),
                media != null ? media.id() : null,
                task.getOutputMediaVersionId(),
                media != null ? media.currentVersion().url() : null,
                media != null ? media.assetId() : null,
                media != null && media.assetId() != null,
                task.getErrorMsg(),
                task.getParams(),
                task.getProjectId(),
                task.getExecutionRunId(),
                task.getProjectObjectId(),
                task.getIdempotencyKey(),
                task.getCreateTime(),
                task.getUpdateTime());
    }
}
