package com.xuejiai.aaf.module.ai.aigc.configuration.api;

/** 渠道规格版本只读边界。 */
public interface AigcChannelSpecApi {

    AigcChannelSpecVersionView requirePublishedVersion(Long channelSpecVersionId);
}
