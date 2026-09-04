package com.xuejiai.aaf.module.ai.aigc.media.api;

/** AIGC 生成子域写入持久媒体的统一接口。 */
public interface AigcMediaApi {
    AigcMediaView createFromGeneratedFile(AigcGeneratedMediaCommand command);

    AigcMediaView createFromUploadedFile(AigcUploadedMediaCommand command);

    AigcMediaView getByVersionId(Long mediaVersionId, Long userId);

    void lockMediaForReference(Long mediaId, Long userId);

    void deleteExclusiveGeneratedByProject(Long projectId);
}
