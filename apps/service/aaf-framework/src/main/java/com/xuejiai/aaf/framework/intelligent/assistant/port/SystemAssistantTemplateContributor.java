package com.xuejiai.aaf.framework.intelligent.assistant.port;

import java.util.List;

import com.xuejiai.aaf.framework.intelligent.assistant.model.AssistantDefinition;

/** 产品模块向通用安装链贡献系统 Assistant 定义的契约。 */
public interface SystemAssistantTemplateContributor {

    List<AssistantDefinition> templates();
}
