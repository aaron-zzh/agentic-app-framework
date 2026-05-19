package com.xuejiai.aaf.framework.messaging;

import java.io.StringWriter;
import java.util.Map;

import org.springframework.stereotype.Component;

import freemarker.template.Configuration;
import freemarker.template.Template;
import lombok.extern.slf4j.Slf4j;

/** 基于 FreeMarker 的消息模板渲染引擎。 */
@Slf4j
@Component
public class MessageTemplateEngine {

    private final Configuration freemarkerConfig;

    public MessageTemplateEngine() {
        this.freemarkerConfig = new Configuration(Configuration.VERSION_2_3_34);
        this.freemarkerConfig.setDefaultEncoding("UTF-8");
    }

    /** 渲染模板内容 */
    public String render(String templateContent, Map<String, Object> variables) {
        try {
            var template = new Template("msg", new java.io.StringReader(templateContent), freemarkerConfig);
            var writer = new StringWriter();
            template.process(variables, writer);
            return writer.toString();
        } catch (Exception e) {
            log.error("模板渲染失败: {}", e.getMessage(), e);
            throw new RuntimeException("模板渲染失败", e);
        }
    }
}
