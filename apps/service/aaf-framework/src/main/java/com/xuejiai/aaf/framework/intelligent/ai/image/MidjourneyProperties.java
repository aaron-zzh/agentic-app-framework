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

    /** 回调验签密钥（M24）：配置后回调 URL 自动附带 ?secret=，回调时校验；未配置则拒绝所有回调（fail-closed） */
    private String notifySecret;
}
