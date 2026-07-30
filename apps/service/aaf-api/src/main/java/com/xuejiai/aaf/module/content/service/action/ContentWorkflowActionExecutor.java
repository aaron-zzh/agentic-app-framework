package com.xuejiai.aaf.module.content.service.action;

import static com.xuejiai.aaf.common.exception.ExceptionUtil.exception;
import static com.xuejiai.aaf.module.content.ErrorCodeConstants.CONTENT_EXECUTION_TARGET_INVALID;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.common.enums.content.ContentExecutionTargetTypeEnum;

/**
 * Workflow 动作执行器。
 *
 * <p>待 Workflow runtime 提供稳定的非 SSE 执行与终态回调契约后接入；当前不配置 Workflow 绑定。
 *
 * @author AaronZZH & Kiro
 */
@Component
public class ContentWorkflowActionExecutor implements ContentActionExecutor {

    @Override
    public boolean supports(String targetType) {
        return ContentExecutionTargetTypeEnum.WORKFLOW.getCode().equals(targetType);
    }

    @Override
    public void execute(ContentActionContext context) {
        throw exception(CONTENT_EXECUTION_TARGET_INVALID);
    }
}
