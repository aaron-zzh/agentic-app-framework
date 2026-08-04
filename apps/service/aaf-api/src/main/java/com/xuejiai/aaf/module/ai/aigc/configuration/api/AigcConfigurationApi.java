package com.xuejiai.aaf.module.ai.aigc.configuration.api;

/** AIGC 项目配置解析边界。 */
public interface AigcConfigurationApi {

    AigcResolvedConfiguration resolve(AigcConfigurationResolveCommand command);
}
