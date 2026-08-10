package com.xuejiai.aaf.module.ai.aigc.media.api;

/** 跨模块只读媒体视图。 */
public interface AigcMediaView {
    Long id();

    AigcMediaType mediaType();

    Long assetId();

    AigcMediaVersionView currentVersion();
}
