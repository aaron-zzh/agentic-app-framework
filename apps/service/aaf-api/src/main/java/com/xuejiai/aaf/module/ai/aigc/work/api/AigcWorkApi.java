package com.xuejiai.aaf.module.ai.aigc.work.api;

public interface AigcWorkApi {

    AigcWorkView collect(AigcWorkCollectCommand command);

    AigcPublicationView publish(AigcWorkPublishCommand command);

    AigcPublicationView markPublicationResult(AigcPublicationResultCommand command);

    AigcWorkView archive(Long workId, Integer expectedVersion);

    void deleteProjectResources(Long projectId);
}
