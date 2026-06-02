package com.xuejiai.aaf.module.customerservice.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/** 企微客服配置属性 */
@Data
@ConfigurationProperties(prefix = "aaf.wecom.kf")
public class WecomKfProperties {

    /** 是否启用 */
    private boolean enabled = false;

    /** 企业ID */
    private String corpId;

    /** 应用密钥 */
    private String appSecret;

    /** 回调Token */
    private String token;

    /** 回调加密Key */
    private String encodingAesKey;

    /** 默认绑定的 Assistant ID（所有客服账号共用） */
    private String defaultAssistantId;

    /** 按客服账号绑定不同 Assistant（优先级高于 defaultAssistantId）。 key = open_kf_id, value = assistantId */
    private Map<String, String> accountAssistantMapping = new HashMap<>();

    /** 兜底回复（Assistant 不可用时） */
    private String fallbackReply = "感谢您的咨询，我暂时无法回答这个问题，已为您转接人工客服。";

    /** 根据客服账号获取对应的 assistantId */
    public String getAssistantId(String openKfId) {
        return accountAssistantMapping.getOrDefault(openKfId, defaultAssistantId);
    }
}
