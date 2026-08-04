package com.xuejiai.aaf.module.ai.aigc.execution.service;

public interface AigcActionExecutor {

    boolean supports(String targetType);

    void execute(AigcActionContext context);
}
