package com.xuejiai.aaf.framework.intelligent.ai.image;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;

/** Midjourney 代理配置（holdai.top 或其他 mj-proxy 兼容代理）。 */
@Data
@Component
@ConfigurationProperties(prefix = "aaf.ai.midjourney")
public class MidjourneyProperties {

    /** 是否启用 */
    private boolean enabled = false;

    /** 代理 base URL，如 https://api.holdai.top/mj */
    private String baseUrl = "https://api.holdai.top/mj";

    /** API Key */
    private String apiKey;

    /** Webhook 回调地址（可选，不配置则使用轮询模式） */
    private String notifyUrl;
}
