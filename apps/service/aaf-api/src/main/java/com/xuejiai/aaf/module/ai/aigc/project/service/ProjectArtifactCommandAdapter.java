package com.xuejiai.aaf.module.ai.aigc.project.service;

import org.springframework.stereotype.Service;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcDocumentCandidateCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcObjectVersionView;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcProjectApi;
import com.xuejiai.aaf.module.ai.aigc.project.api.AigcTextCandidateCommand;
import com.xuejiai.aaf.module.ai.aigc.project.api.ProjectArtifactCommandApi;

import lombok.RequiredArgsConstructor;

/** Assistant Tool 适配器，只校验绑定身份并复用项目 candidate 原子写入链。 */
@Service
@RequiredArgsConstructor
public class ProjectArtifactCommandAdapter implements ProjectArtifactCommandApi {

    private final AigcProjectApi projectApi;

    @Override
    public AigcObjectVersionView recordTextCandidate(
            AigcTextCandidateCommand command, InvocationContext invocationContext) {
        requireBoundInvocation(command.projectId(), invocationContext);
        return projectApi.recordTextCandidate(command);
    }

    @Override
    public AigcObjectVersionView recordDocumentCandidate(
            AigcDocumentCandidateCommand command, InvocationContext invocationContext) {
        requireBoundInvocation(command.projectId(), invocationContext);
        return projectApi.recordDocumentCandidate(command);
    }

    private void requireBoundInvocation(Long projectId, InvocationContext context) {
        if (context.taskId() == null
                || context.executionId() == null
                || context.idempotencyKey() == null
                || context.dispatchId() == null) {
            throw new IllegalArgumentException("项目写 Tool 必须绑定 Task/Execution/dispatch/toolCall 身份");
        }
        if (context.projectId() == null || !context.projectId().equals(projectId)) {
            throw new IllegalArgumentException("InvocationContext projectId 与项目不一致");
        }
        var project = projectApi.requireProject(projectId);
        if (context.workspaceId() == null || !context.workspaceId().equals(project.workspaceId())) {
            throw new IllegalArgumentException("InvocationContext workspace 与项目不一致");
        }
    }
}
