package com.xuejiai.aaf.module.ai.aigc.execution.service;

import org.springframework.stereotype.Component;

import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcRuntimeExecution.Command;
import com.xuejiai.aaf.module.ai.aigc.execution.api.AigcWorkflowExecutionPort;

import lombok.RequiredArgsConstructor;

/** Workflow 动作执行器。 */
@Component
@RequiredArgsConstructor
public class AigcWorkflowActionExecutor implements AigcActionExecutor {

    private final AigcWorkflowExecutionPort executionPort;
    private final AigcRuntimeCompletionService completionService;

    @Override
    public boolean supports(String targetType) {
        return "workflow".equals(targetType);
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
                run.getEffectiveInput());
    }
}
