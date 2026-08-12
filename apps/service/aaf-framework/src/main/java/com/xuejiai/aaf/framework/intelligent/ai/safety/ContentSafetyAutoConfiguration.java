package com.xuejiai.aaf.framework.intelligent.ai.safety;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import com.xuejiai.aaf.framework.intelligent.assistant.hitl.ToolApprovalService;

/** 生成式内容安全服务自动配置。 */
@AutoConfiguration
public class ContentSafetyAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ContentSafetyService.class)
    ContentSafetyService contentSafetyService(ToolApprovalService approvalService) {
        return new NoopContentSafetyService(approvalService);
    }
}
