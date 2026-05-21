package com.xuejiai.aaf.framework.messaging;

import java.util.Optional;

/** 消息模板提供者接口，由业务层实现（从数据库加载模板）。 */
public interface MessageTemplateProvider {

    /** 根据模板编码查找模板内容 */
    Optional<MessageTemplateInfo> findByCode(String code);

    /** 模板信息 */
    record MessageTemplateInfo(
            String code, MessageChannel channel, String subject, String content) {}
}
