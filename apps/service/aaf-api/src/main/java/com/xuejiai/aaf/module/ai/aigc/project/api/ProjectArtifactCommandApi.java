package com.xuejiai.aaf.module.ai.aigc.project.api;

import com.xuejiai.aaf.framework.intelligent.agent.model.InvocationContext;

/** Assistant 生成正文后的类型化 candidate 登记端口；本端口不调用模型。 */
public interface ProjectArtifactCommandApi {

    AigcObjectVersionView recordTextCandidate(
            AigcTextCandidateCommand command, InvocationContext invocationContext);

    AigcObjectVersionView recordDocumentCandidate(
            AigcDocumentCandidateCommand command, InvocationContext invocationContext);
}
