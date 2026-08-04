package com.xuejiai.aaf.module.ai.aigc.execution.service;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcAgentExecutionPort;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Command;

import lombok.RequiredArgsConstructor;

/** Agent 动作执行器。 */
@Component
@RequiredArgsConstructor
public class AigcAgentActionExecutor implements AigcActionExecutor {

    private final AigcAgentExecutionPort executionPort;
    private final AigcRuntimeCompletionService completionService;

    @Override
    public boolean supports(String targetType) {
        return "agent".equals(targetType);
    }

    @Override
    public void execute(AigcActionContext context) {
        var submission = executionPort.submit(toCommand(context));
        completionService.track(context, submission);
    }

    private Command toCommand(AigcActionContext context) {
        var run = context.executionRun();
        var project = context.project();
        var prompt = context.command().prompt();
        return new Command(
                run.getId(),
                run.getProjectId(),
                run.getObjectId(),
                project.orgId(),
                project.workspaceId(),
                project.userId(),
                context.binding().getTargetRef(),
                run.getActionKey(),
                prompt == null || prompt.isBlank() ? project.brief() : prompt,
                context.command().idempotencyKey(),
                run.getInputPayload());
    }
}
