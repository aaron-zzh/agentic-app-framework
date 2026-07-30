package com.xuejiai.aaf.module.content.service.action;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_EXECUTION_TARGET_INVALID;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.content.ContentExecutionTargetTypeEnum;

/**
 * Agent 动作执行器。
 *
 * <p>待 Agent runtime 提供稳定的同步执行与终态回调契约后接入；当前不配置 Agent 绑定。
 *
 * @author AaronZZH & Kiro
 */
@Component
public class ContentAgentActionExecutor implements ContentActionExecutor {

    @Override
    public boolean supports(String targetType) {
        return ContentExecutionTargetTypeEnum.AGENT.getCode().equals(targetType);
    }

    @Override
    public void execute(ContentActionContext context) {
        throw exception(CONTENT_EXECUTION_TARGET_INVALID);
    }
}
