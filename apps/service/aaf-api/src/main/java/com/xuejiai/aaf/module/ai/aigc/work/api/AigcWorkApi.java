package com.xuejiai.aaf.module.ai.aigc.work.api;

public interface AigcWorkApi {

    AigcWorkView collect(AigcWorkCollectCommand command);

    AigcPublicationView publish(AigcWorkPublishCommand command);

    AigcPublicationView cancelPublication(AigcPublicationCancelCommand command);

    AigcPublicationView retryPublication(AigcPublicationRetryCommand command);

    /** 仅渠道执行适配器调用，不暴露普通用户 REST。 */
    AigcPublicationView markPublicationResult(AigcPublicationResultCommand command);

    AigcWorkView archive(AigcWorkArchiveCommand command);
}
